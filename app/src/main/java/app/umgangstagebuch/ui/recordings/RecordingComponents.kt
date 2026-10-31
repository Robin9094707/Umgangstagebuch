package app.umgangstagebuch.ui.recordings

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Wellenform mit Fortschritt; tippen/ziehen springt an die Stelle. */
@Composable
fun WaveformBars(
    waveform: List<Int>,
    progress: Float,
    modifier: Modifier = Modifier,
    playedColor: Color,
    remainingColor: Color,
    onSeek: ((Float) -> Unit)? = null,
) {
    val bars = remember(waveform) { resample(waveform, 64) }
    Canvas(
        modifier = modifier.then(
            if (onSeek != null) Modifier.pointerInput(bars) {
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }.pointerInput(bars) {
                detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f)) }
            } else Modifier,
        ),
    ) {
        if (bars.isEmpty()) return@Canvas
        val n = bars.size
        val gap = size.width * 0.3f / n
        val barW = (size.width - gap * (n - 1)) / n
        val mid = size.height / 2
        bars.forEachIndexed { i, v ->
            val h = max(size.height * 0.06f, size.height * 0.94f * v)
            val x = i * (barW + gap)
            val played = (i + 0.5f) / n <= progress
            drawRoundRect(
                color = if (played) playedColor else remainingColor,
                topLeft = Offset(x, mid - h / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

/** Laufende Wellenform während der Aufnahme (neueste Werte rechts). */
@Composable
fun LiveWaveform(levels: List<Float>, modifier: Modifier = Modifier, color: Color, barWidth: Dp = 4.dp) {
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val bw = barWidth.toPx()
        val gap = bw * 0.6f
        val count = (size.width / (bw + gap)).toInt().coerceAtLeast(1)
        val shown = levels.takeLast(count)
        val mid = size.height / 2
        shown.forEachIndexed { i, v ->
            val h = max(size.height * 0.04f, size.height * 0.96f * v)
            val x = size.width - (shown.size - i) * (bw + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, mid - h / 2),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2, bw / 2),
            )
        }
    }
}

private fun resample(values: List<Int>, target: Int): List<Float> {
    if (values.isEmpty()) return emptyList()
    val out = FloatArray(target)
    val bucket = values.size.toDouble() / target
    for (i in 0 until target) {
        val from = (i * bucket).toInt().coerceIn(0, values.size - 1)
        val to = ((i + 1) * bucket).toInt().coerceIn(from + 1, values.size)
        var mx = 0
        for (j in from until to) mx = max(mx, values[j])
        out[i] = (mx / 100f).coerceIn(0f, 1f)
    }
    // leicht anheben, damit leise Stellen sichtbar bleiben
    return out.map { max(it, it * 0.85f + 0.04f) }
}

@Stable
class AudioPlayerState {
    var playing by mutableStateOf(false)
        internal set
    var positionMs by mutableLongStateOf(0L)
        internal set
    var durationMs by mutableIntStateOf(0)
        internal set
    internal var player: MediaPlayer? = null

    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val target = (fraction.coerceIn(0f, 1f) * p.duration).toInt()
        p.seekTo(target)
        positionMs = target.toLong()
    }
}

@Composable
fun rememberAudioPlayer(uri: Uri?, fallbackDurationMs: Long): AudioPlayerState {
    val context = LocalContext.current
    val state = remember(uri) { AudioPlayerState() }
    DisposableEffect(uri) {
        val mp = MediaPlayer()
        state.player = mp
        state.durationMs = fallbackDurationMs.toInt()
        if (uri != null) {
            runCatching {
                mp.setDataSource(context, uri)
                mp.setOnPreparedListener { state.durationMs = if (it.duration > 0) it.duration else fallbackDurationMs.toInt() }
                mp.setOnCompletionListener { state.playing = false; state.positionMs = state.durationMs.toLong() }
                mp.prepareAsync()
            }
        }
        onDispose { runCatching { mp.release() }; state.player = null }
    }
    LaunchedEffect(state.playing) {
        while (state.playing) {
            state.player?.let { if (it.isPlaying) state.positionMs = it.currentPosition.toLong() }
            kotlinx.coroutines.delay(50)
        }
    }
    return state
}

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
