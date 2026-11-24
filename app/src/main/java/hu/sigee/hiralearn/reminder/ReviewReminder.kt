package hu.sigee.hiralearn.reminder

import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hu.sigee.hiralearn.MainActivity
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.core.Reminder
import hu.sigee.hiralearn.data.ReminderSettings
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Daily review reminder: a one-time WorkManager job at the chosen time that re-schedules itself for the next day.
 * A chain of one-time jobs (rather than a 24 h periodic job) keeps the reminder at the chosen wall-clock time,
 * across daylight-saving changes and without the drift of periodic work.
 */
object ReviewReminder {
    private const val WORK_NAME = "daily_review_reminder"
    private const val CHANNEL_ID = "review_reminder"
    private const val NOTIFICATION_ID = 1

    /**
     * Applies [settings]: schedules the next reminder or cancels it. [replace] = false keeps an already scheduled job
     * (app start); true moves it (the user changed the time).
     */
    fun apply(context: Context, settings: ReminderSettings, now: Long, replace: Boolean = true) {
        val work = WorkManager.getInstance(context)
        if (!settings.enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        enqueue(work, settings, now, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(work: WorkManager, settings: ReminderSettings, now: Long, policy: ExistingWorkPolicy) {
        val delay = Reminder.nextTrigger(now, settings.time, ZoneId.systemDefault()) - now
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        work.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** Whether the system lets us post notifications (permission on Android 13+, and not blocked by the user). */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal suspend fun runDaily(context: Context) {
        val container = context.appContainer
        val settings = container.settings.reminder.value
        if (!settings.enabled) return
        val now = container.clock()
        val count = Reminder.dueKanaCount(container.progress.snapshot(), now)
        if (count > 0) notify(context, count)
        // Runs while this job is still RUNNING, so APPEND_OR_REPLACE chains the next day's job after it instead of
        // cancelling the current one.
        enqueue(WorkManager.getInstance(context), settings, now, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun notify(context: Context, count: Int) {
        if (!canNotify(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.seal))
            .setContentTitle(context.resources.getQuantityString(R.plurals.reminder_title, count, count))
            .setContentText(context.getString(R.string.reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // Same id every day: today's reminder replaces yesterday's if it was never opened.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to show.
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.reminder_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReviewReminder.runDaily(applicationContext)
        return Result.success()
    }
}
