package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.InspirationQuote
import com.example.domain.solat.SolatSchedule
import java.util.Calendar
import java.util.TimeZone

object NoorNotificationHelper {

    const val CHANNEL_DAILY_INSPIRATION = "noor_daily_inspiration"
    const val CHANNEL_PRAYER_REMINDERS = "noor_prayer_reminders"

    const val ACTION_DAILY_INSPIRATION = "com.example.noor.ACTION_DAILY_INSPIRATION"
    const val ACTION_PRAYER_REMINDER = "com.example.noor.ACTION_PRAYER_REMINDER"

    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_PRAYER_TIME = "extra_prayer_time"
    const val EXTRA_LOCATION_LABEL = "extra_location_label"

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val inspirationChannel = NotificationChannel(
                CHANNEL_DAILY_INSPIRATION,
                "Daily Quran & Hadith",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily verse or Hadith inspiration for reflection."
            }
            val prayerChannel = NotificationChannel(
                CHANNEL_PRAYER_REMINDERS,
                "Solat Prayer Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders when daily Solat prayer times begin."
            }
            manager.createNotificationChannel(inspirationChannel)
            manager.createNotificationChannel(prayerChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showDailyInspirationNotification(context: Context, quote: InspirationQuote) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_INSPIRATION)
            .setSmallIcon(R.drawable.ic_noor_notif)
            .setContentTitle("${quote.sourceType.displayLabel} • ${quote.reference}")
            .setContentText(quote.translation)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${quote.translation}\n— ${quote.sourceDomain}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(2001, notification)
        }
    }

    fun showPrayerReminderNotification(
        context: Context,
        prayerName: String,
        timeLabel: String,
        locationLabel: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            1002,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PRAYER_REMINDERS)
            .setSmallIcon(R.drawable.ic_noor_notif)
            .setContentTitle("Solat $prayerName Time ($timeLabel)")
            .setContentText("It is time for $prayerName prayer in $locationLabel.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(3000 + prayerName.hashCode(), notification)
        }
    }

    fun scheduleDailyInspiration(context: Context, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NoorReminderReceiver::class.java).apply {
            action = ACTION_DAILY_INSPIRATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        val triggerCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerCal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun schedulePrayerReminders(context: Context, schedule: SolatSchedule, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val obligatory = schedule.entries.filter { it.isObligatory }
        obligatory.forEachIndexed { index, entry ->
            val requestCode = 5000 + index
            val intent = Intent(context, NoorReminderReceiver::class.java).apply {
                action = ACTION_PRAYER_REMINDER
                putExtra(EXTRA_PRAYER_NAME, entry.name)
                putExtra(EXTRA_PRAYER_TIME, entry.time.format12Hour())
                putExtra(EXTRA_LOCATION_LABEL, schedule.locationLabel)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            if (!enabled) return@forEachIndexed

            val tz = TimeZone.getTimeZone(schedule.timeZoneId)
            val triggerCal = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, entry.time.hour)
                set(Calendar.MINUTE, entry.time.minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerCal.timeInMillis,
                pendingIntent
            )
        }
    }
}
