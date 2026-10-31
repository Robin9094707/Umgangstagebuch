package app.umgangstagebuch.recording

import app.umgangstagebuch.data.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gemeinsamer Zustand zwischen Aufnahmedienst und Oberfläche.
 * So kann die App jederzeit sehen, ob (und wie lange) gerade aufgenommen wird –
 * auch wenn man zwischendurch andere Apps benutzt.
 */
object Recorder {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Aktueller Pegel 0..1 (geglättet), für den pulsierenden Aufnahmeknopf. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Die letzten Pegelwerte für die laufende Wellenform. */
    private val _liveWave = MutableStateFlow<List<Float>>(emptyList())
    val liveWave: StateFlow<List<Float>> = _liveWave.asStateFlow()

    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()

    /** Ort, den die App beim Start ermittelt hat; der Dienst speichert ihn mit. */
    @Volatile var pendingLocation: GeoPoint? = null

    fun onStarted(startedAt: Long) {
        _startedAt.value = startedAt
        _active.value = true
        _paused.value = false
        _liveWave.value = emptyList()
        _level.value = 0f
    }

    fun onLevel(normalized: Float) {
        _level.value = normalized
        _liveWave.value = (_liveWave.value + normalized).takeLast(96)
    }

    fun onPaused(paused: Boolean) { _paused.value = paused }

    fun onFinishing(finishing: Boolean) { _finishing.value = finishing }

    fun onStopped() {
        _active.value = false
        _paused.value = false
        _level.value = 0f
        _startedAt.value = 0L
        _liveWave.value = emptyList()
        pendingLocation = null
    }
}
