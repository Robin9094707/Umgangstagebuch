package app.umgangstagebuch.reminders

import android.Manifest
import android.app.Notification
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.umgangstagebuch.MainActivity
import app.umgangstagebuch.R
import app.umgangstagebuch.UmgangApp
import app.umgangstagebuch.data.AppData
import app.umgangstagebuch.data.RepoState
import app.umgangstagebuch.data.Settings
import app.umgangstagebuch.data.VisitStatus
import app.umgangstagebuch.data.VisitType
import app.umgangstagebuch.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL_VISITS = "umgaenge"
    const val CHANNEL_JOURNAL = "tagebuch"
    const val CHANNEL_RECORDING = "aufnahme"
    const val EXTRA_ROUTE = "route"
    private const val TAG = "visit-reminder"
    private const val WEEKLY = "weekly-reflection"
    private const val HOUR = 3_600_000L

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_VISITS, "Umgangstermine", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Erinnerungen vor und nach einem Umgang" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_JOURNAL, "Tagebuch", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Sanfte Erinnerung, Beobachtungen festzuhalten" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Aufnahme", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt an, dass eine Tonaufnahme läuft (von Android vorgeschrieben)"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
    }

    fun reschedule(context: Context, data: AppData, s: Settings) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        val now = System.currentTimeMillis()
        val horizon = now + 60L * 24 * HOUR
        val child = data.profile.childDisplay
        val father = data.profile.fatherDisplay

        data.visits.filter { it.status == VisitStatus.PLANNED && it.start in now..horizon }.forEach { v ->
            if (!s.visitReminders) return@forEach
            val at = v.start - s.reminderHoursBefore * HOUR
            if (at > now) {
                val title = "Umgang ${Fmt.relativeDay(v.start, at)} um ${Fmt.time(v.start)} Uhr"
                val text = "$child ${if (v.type == VisitType.PHONE || v.type == VisitType.VIDEO) "hat einen ${v.type.label}" else "ist bei"} $father" +
                    (v.location.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                enqueue(wm, at - now, "before", v.id, title, text, "visit/${v.id}")
            }
        }
        if (s.afterVisitReminder) {
            val reported = data.entries.mapNotNull { it.visitId }.toSet()
            data.visits.filter {
                it.status == VisitStatus.PLANNED && it.id !in reported && it.end + HOUR > now && it.end < horizon &&
                    it.type != VisitType.PHONE && it.type != VisitType.VIDEO
            }.forEach { v ->
                enqueue(
                    wm, v.end + HOUR - now, "after", v.id,
                    "Wie war der Umgang?",
                    "Halte jetzt in 2 Minuten fest, wie es $child ging – solange alles frisch ist.",
                    "report/${v.id}",
                )
            }
        }

        if (s.weeklyReminder) {
            val next = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
            val delay = Fmt.millis(next, LocalTime.of(19, 30)) - now
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "kind" to "weekly",
                        "title" to "Wochenrückblick",
                        "text" to "Gab es diese Woche etwas zu $child, das du festhalten möchtest?",
                        "route" to "entry_new",
                    ),
                ).build()
            wm.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(WEEKLY)
        }
    }

    private fun enqueue(wm: WorkManager, delay: Long, kind: String, visitId: String, title: String, text: String, route: String) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(workDataOf("kind" to kind, "visitId" to visitId, "title" to title, "text" to text, "route" to route))
            .build()
        wm.enqueue(req)
    }

    fun notify(context: Context, id: Int, channel: String, title: String, text: String, route: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
        }
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Umgangstagebuch")
            .setContentText("Neue Erinnerung")
            .build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: return Result.success()
        val visitId = inputData.getString("visitId")
        val repo = (applicationContext as UmgangApp).repository
        if (repo.state.value == RepoState.Loading) runCatching { repo.init() }
        val data = repo.data.value
        val visit = visitId?.let { id -> data.visits.firstOrNull { it.id == id } }

        // Veraltete Erinnerungen nicht mehr zeigen
        if (visitId != null && repo.state.value == RepoState.Ready) {
            if (visit == null || visit.status == VisitStatus.CANCELLED || visit.status == VisitStatus.NO_SHOW) return Result.success()
            if (kind == "after" && data.entries.any { it.visitId == visitId }) return Result.success()
        }
        Reminders.notify(
            applicationContext,
            id = (visitId ?: kind).hashCode() + kind.hashCode(),
            channel = if (kind == "weekly") Reminders.CHANNEL_JOURNAL else Reminders.CHANNEL_VISITS,
            title = inputData.getString("title").orEmpty(),
            text = inputData.getString("text").orEmpty(),
            route = inputData.getString("route"),
        )
        return Result.success()
    }
}
