package app.propcare.homeos.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.propcare.homeos.MainActivity
import app.propcare.homeos.R
import app.propcare.homeos.data.AppDb
import app.propcare.homeos.data.Settings
import app.propcare.homeos.data.summarise
import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.util.Format
import app.propcare.homeos.widget.HomeWidget
import java.util.concurrent.TimeUnit

/**
 * Daily: notifies when a safety check is overdue or due within 30 days. Each check is
 * notified once per state (key "propertyId:kind:state"), so nobody gets the same alert every day.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val dao = AppDb.get(ctx).dao()
        val settings = Settings(ctx)
        val summaries = summarise(dao.propertiesNow(), dao.complianceNow(), dao.jobsNow())

        data class Due(val key: String, val propertyId: Long, val title: String, val text: String)
        val due = summaries.flatMap { s ->
            s.checks.filter { it.state == CheckState.overdue || it.state == CheckState.due_soon }.map { c ->
                Due("${s.property.id}:${c.rule.kind}:${c.state.name}", s.property.id, "${c.rule.name}: ${Format.checkState(c).lowercase()}", "${s.property.name}. Tap to book it with PropCare.")
            }
        }
        val current = due.map { it.key }.toSet()
        val already = settings.notifiedKeys
        val fresh = due.filter { it.key !in already }

        if (fresh.isNotEmpty() && canNotify(ctx)) {
            ensureChannel(ctx)
            fresh.forEach { notify(ctx, it.key.hashCode(), it.title, it.text, it.propertyId) }
            // Keep only keys still current, so a check that becomes due again later is notified again.
            settings.notifiedKeys = (already intersect current) + fresh.map { it.key }
        } else {
            settings.notifiedKeys = already intersect current
        }
        HomeWidget.refresh(ctx)
        return Result.success()
    }

    private fun canNotify(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission") // checked in canNotify
    private fun notify(ctx: Context, id: Int, title: String, text: String, propertyId: Long) {
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_PROPERTY_ID, propertyId)
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_propcare)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        private const val WORK_NAME = "safety-check-reminders"

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_REMINDERS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_REMINDERS, "Safety check reminders", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Gas safety, EICR, EPC and alarm checks that are due soon or overdue" },
                )
            }
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
