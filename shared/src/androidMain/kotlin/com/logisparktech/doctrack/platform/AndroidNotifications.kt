package com.logisparktech.doctrack.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.logisparktech.doctrack.shared.R
import com.logisparktech.doctrack.core.localization.stringsFor
import com.logisparktech.doctrack.core.notification.NotificationScheduler
import com.logisparktech.doctrack.core.notification.ReminderSyncService
import com.logisparktech.doctrack.core.notification.ScheduledNotification
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

private const val CHANNEL_ID = "doctrack_expiry_reminders"
private const val PREFS = "doctrack_notifications"
private const val KEY_IDS = "scheduled_ids"
private const val EXTRA_ID = "doctrack_notification_id"
private const val EXTRA_TITLE = "doctrack_title"
private const val EXTRA_BODY = "doctrack_body"

/**
 * Uses AlarmManager with inexact, doze-friendly alarms (no exact-alarm permission needed).
 * Scheduled ids are persisted so everything can be cancelled and re-created idempotently.
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {
    override val maxPending: Int = 400

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun hasPermission(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override suspend fun replaceAll(notifications: List<ScheduledNotification>) = withContext(Dispatchers.IO) {
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        previous.forEach { id -> alarmManager.cancel(pendingIntent(id, null)) }
        notifications.forEach { n ->
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.triggerAtMillis, pendingIntent(n.id, n))
        }
        prefs.edit().putStringSet(KEY_IDS, notifications.map { it.id.toString() }.toSet()).apply()
    }

    private fun pendingIntent(id: Int, n: ScheduledNotification?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.logisparktech.doctrack.REMINDER_$id"
            if (n != null) {
                putExtra(EXTRA_ID, n.id)
                putExtra(EXTRA_DOCUMENT_ID, n.documentId)
                putExtra(EXTRA_TITLE, n.title)
                putExtra(EXTRA_BODY, n.body)
            }
        }
        return PendingIntent.getBroadcast(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

fun ensureNotificationChannel(context: Context, name: String, description: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            this.description = description
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Posts the reminder when its alarm fires. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val documentId = intent.getLongExtra(EXTRA_DOCUMENT_ID, -1L)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_DOCUMENT_ID, documentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val content = launch?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_doctrack)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(EXTRA_BODY)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

/** Alarms are cleared on reboot, app update or clock/timezone change; re-create them from the database. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ensureKoin(context.applicationContext)
                val koin = KoinPlatform.getKoin()
                val strings = stringsFor(koin.get<SettingsRepository>().current().language)
                ensureNotificationChannel(context, strings.notificationChannelName, strings.notificationChannelDescription)
                koin.get<ReminderSyncService>().resync()
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
