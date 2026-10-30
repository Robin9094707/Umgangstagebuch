package app.umgangstagebuch

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.umgangstagebuch.data.RepoState
import app.umgangstagebuch.reminders.Reminders
import app.umgangstagebuch.ui.AppRoot
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    val locked = mutableStateOf(false)
    val pendingRoute = mutableStateOf<String?>(null)
    private var stoppedAt = 0L
    private var authenticating = false

    private val app get() = application as UmgangApp

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = app.repository
        splash.setKeepOnScreenCondition { repo.state.value == RepoState.Loading }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.08f).scaleY(1.08f)
                .setDuration(320L)
                .withEndAction { provider.remove() }
                .start()
        }

        if (savedInstanceState == null) locked.value = repo.prefs.current.appLock
        lifecycleScope.launch { repo.init() }
        handleIntent(intent)

        setContent {
            AppRoot(
                app = app,
                locked = locked.value,
                onUnlock = ::authenticate,
                pendingRoute = pendingRoute.value,
                onRouteConsumed = { pendingRoute.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Reminders.EXTRA_ROUTE)?.let {
            pendingRoute.value = it
            intent.removeExtra(Reminders.EXTRA_ROUTE)
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = app.repository.prefs.current
        if (prefs.appLock && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) {
            locked.value = true
        }
        if (locked.value) window.decorView.post { authenticate() }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun authenticate() {
        if (authenticating || !locked.value) return
        authenticating = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    // Kein Bildschirmschutz eingerichtet → Sperre nicht erzwingen
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                        locked.value = false
                    }
                }

                override fun onAuthenticationFailed() = Unit
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Umgangstagebuch entsperren")
            .setSubtitle("Deine Einträge sind geschützt")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure {
            authenticating = false
            locked.value = false
        }
    }

    companion object {
        private const val LOCK_AFTER_MS = 2 * 60 * 1000L
    }
}
