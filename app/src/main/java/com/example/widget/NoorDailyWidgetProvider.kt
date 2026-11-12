package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.NoorPreferencesRepository
import com.example.data.repository.InspirationRepository
import com.example.domain.solat.SolatCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class NoorDailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                renderWidgets(context, appWidgetManager, appWidgetIds, forceCycle = false)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_REFRESH) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, NoorDailyWidgetProvider::class.java)
                    )
                    renderWidgets(context, manager, ids, forceCycle = true)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.example.noor.ACTION_WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NoorDailyWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                renderWidgets(context, manager, ids, forceCycle = false)
            }
        }

        private suspend fun renderWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            forceCycle: Boolean
        ) {
            if (appWidgetIds.isEmpty()) return
            val prefsRepo = NoorPreferencesRepository(context.applicationContext)
            val settings = prefsRepo.readSettingsOnce()
            val tz = TimeZone.getTimeZone(settings.timeZoneId)
            val cal = Calendar.getInstance(tz)
            val schedule = SolatCalculator.calculateDaySchedule(
                calendar = cal,
                latitude = settings.latitude,
                longitude = settings.longitude,
                timeZone = tz,
                locationLabel = settings.locationLabel,
                method = settings.calculationMethod
            )
            val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val (nextEntry, _) = schedule.nextPrayer(nowMinutes)
            val nextPrayerLabel = "${nextEntry.name} ${nextEntry.time.format12Hour()}"

            val snapshot = if (forceCycle) {
                val randomQuote = InspirationRepository.curatedQuotes.random()
                prefsRepo.saveWidgetSnapshot(randomQuote, nextPrayerLabel)
                prefsRepo.readWidgetSnapshot()
            } else {
                prefsRepo.readWidgetSnapshot() ?: run {
                    val seeded = InspirationRepository().getDailySeededQuote(cal.get(Calendar.DAY_OF_YEAR))
                    prefsRepo.saveWidgetSnapshot(seeded, nextPrayerLabel)
                    prefsRepo.readWidgetSnapshot()
                }
            }

            val refText = snapshot?.reference ?: "Surah Ash-Sharh 94:5–6"
            val arabicText = snapshot?.arabicText.orEmpty()
            val translationText = snapshot?.translation ?: "So, surely with hardship comes ease."
            val sourceLabel = snapshot?.sourceType?.uppercase() ?: "AL-QURAN"

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val refreshIntent = Intent(context, NoorDailyWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_noor_daily).apply {
                    setTextViewText(R.id.widget_source_badge, "NOOR • $sourceLabel")
                    setTextViewText(R.id.widget_next_prayer, nextPrayerLabel)
                    if (arabicText.isBlank()) {
                        setViewVisibility(R.id.widget_arabic, View.GONE)
                    } else {
                        setViewVisibility(R.id.widget_arabic, View.VISIBLE)
                        setTextViewText(R.id.widget_arabic, arabicText)
                    }
                    setTextViewText(R.id.widget_translation, translationText)
                    setTextViewText(R.id.widget_reference, refText)
                    setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                    setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)
                }
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}
