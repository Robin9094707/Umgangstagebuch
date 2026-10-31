package app.umgangstagebuch.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.umgangstagebuch.MainActivity
import app.umgangstagebuch.R
import app.umgangstagebuch.UmgangApp
import app.umgangstagebuch.data.Recording
import app.umgangstagebuch.data.newId
import app.umgangstagebuch.data.sha256
import app.umgangstagebuch.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Nimmt Ton im Vordergrunddienst auf – läuft weiter, wenn der Bildschirm aus ist
 * oder man andere Apps benutzt. Android verlangt dafür eine kleine Benachrichtigung.
 * Start und Stopp steuert die App; die fertige Aufnahme wird unveränderlich gespeichert.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var startedAt = 0L
    private var startElapsed = 0L
    private val samples = ArrayList<Int>(4096)
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val poll = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
            val norm = (amp / 28000f).coerceIn(0f, 1f)
            samples.add((norm * 100).toInt())
            Recorder.onLevel(norm)
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        val temp = File(cacheDir, "recordings").apply { mkdirs() }
            .let { File(it, "rec_${System.currentTimeMillis()}.m4a") }
        val rec = try {
            (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(temp.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            cleanup()
            app.repository.message("Aufnahme konnte nicht gestartet werden: ${e.message ?: "Mikrofon nicht verfügbar"}")
            stopSelf()
            return
        }

        recorder = rec
        tempFile = temp
        startedAt = System.currentTimeMillis()
        startElapsed = SystemClock.elapsedRealtime()
        samples.clear()

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "umgangstagebuch:aufnahme")
            .apply { runCatching { acquire(6 * 60 * 60 * 1000L) } }

        startForegroundCompat()
        Recorder.onStarted(startedAt)
        handler.postDelayed(poll, POLL_MS)
    }

    private fun stopRecording() {
        val rec = recorder
        val temp = tempFile
        handler.removeCallbacks(poll)
        if (rec == null || temp == null) { cleanup(); stopSelf(); return }
        Recorder.onFinishing(true)

        val durationMs = SystemClock.elapsedRealtime() - startElapsed
        val ok = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        recorder = null
        releaseWakeLock()

        if (!ok || durationMs < 700 || !temp.exists() || temp.length() < 256L) {
            runCatching { temp.delete() }
            app.repository.message("Aufnahme war zu kurz und wurde verworfen.")
            finish()
            return
        }

        val wave = downsample(samples.toList(), 1800)
        val started = startedAt
        scope.launch {
            val hash = runCatching { temp.inputStream().use { sha256(it) } }.getOrDefault("")
            val recording = Recording(
                id = newId(),
                fileName = "aufnahme_${started}.m4a",
                startedAt = started,
                createdAt = System.currentTimeMillis(),
                durationMs = durationMs,
                title = "",
                location = Recorder.pendingLocation,
                waveform = wave,
                sha256 = hash,
                mimeType = "audio/mp4",
            )
            val saved = app.repository.addRecording(recording, temp)
            if (saved) app.repository.message("Aufnahme gespeichert ✓")
            finish()
        }
    }

    private fun finish() {
        Recorder.onFinishing(false)
        Recorder.onStopped()
        stopForegroundCompat()
        stopSelf()
    }

    private fun cleanup() {
        handler.removeCallbacks(poll)
        runCatching { recorder?.release() }
        recorder = null
        releaseWakeLock()
        Recorder.onStopped()
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording() else cleanup()
        super.onDestroy()
    }

    private val app get() = applicationContext as UmgangApp

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Reminders.EXTRA_ROUTE, "recordings")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Reminders.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Umgangstagebuch")
            .setContentText("Aufnahme läuft")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(open)
            .addAction(0, "Stoppen", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "app.umgangstagebuch.STOP_RECORDING"
        private const val NOTIF_ID = 4711
        private const val POLL_MS = 100L

        fun start(context: Context) {
            val i = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }

        /** Verlauf gleichmäßig auf höchstens [max] Werte eindampfen. */
        private fun downsample(values: List<Int>, max: Int): List<Int> {
            if (values.size <= max) return values
            val bucket = values.size.toDouble() / max
            return (0 until max).map { i ->
                val from = (i * bucket).toInt()
                val to = ((i + 1) * bucket).toInt().coerceAtMost(values.size)
                if (to > from) values.subList(from, to).max() else values[from]
            }
        }
    }
}
