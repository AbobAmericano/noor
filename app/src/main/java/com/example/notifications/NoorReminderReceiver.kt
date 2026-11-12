package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.preferences.NoorPreferencesRepository
import com.example.data.repository.InspirationRepository
import com.example.domain.solat.SolatCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class NoorReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefsRepo = NoorPreferencesRepository(context.applicationContext)
                val settings = prefsRepo.readSettingsOnce()

                when (intent.action) {
                    NoorNotificationHelper.ACTION_DAILY_INSPIRATION -> {
                        if (settings.dailyQuoteNotificationsEnabled) {
                            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                            val quote = InspirationRepository().getDailySeededQuote(dayOfYear)
                            NoorNotificationHelper.showDailyInspirationNotification(context, quote)
                        }
                    }

                    NoorNotificationHelper.ACTION_PRAYER_REMINDER -> {
                        if (settings.prayerRemindersEnabled) {
                            val name = intent.getStringExtra(NoorNotificationHelper.EXTRA_PRAYER_NAME) ?: "Solat"
                            val time = intent.getStringExtra(NoorNotificationHelper.EXTRA_PRAYER_TIME) ?: ""
                            val loc = intent.getStringExtra(NoorNotificationHelper.EXTRA_LOCATION_LABEL)
                                ?: settings.locationLabel
                            NoorNotificationHelper.showPrayerReminderNotification(context, name, time, loc)
                        }
                    }

                    Intent.ACTION_BOOT_COMPLETED -> {
                        NoorNotificationHelper.scheduleDailyInspiration(
                            context,
                            settings.dailyQuoteNotificationsEnabled
                        )
                        val tz = TimeZone.getTimeZone(settings.timeZoneId)
                        val schedule = SolatCalculator.calculateDaySchedule(
                            calendar = Calendar.getInstance(tz),
                            latitude = settings.latitude,
                            longitude = settings.longitude,
                            timeZone = tz,
                            locationLabel = settings.locationLabel,
                            method = settings.calculationMethod
                        )
                        NoorNotificationHelper.schedulePrayerReminders(
                            context,
                            schedule,
                            settings.prayerRemindersEnabled
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
