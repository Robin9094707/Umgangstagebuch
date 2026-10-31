package app.umgangstagebuch

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import app.umgangstagebuch.data.AppData
import app.umgangstagebuch.data.RepoState
import app.umgangstagebuch.data.ThemeMode
import app.umgangstagebuch.ui.AppRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendert die wichtigsten Bildschirme mit fiktiven Beispieldaten und prüft,
 * dass Navigation und Darstellung ohne Absturz funktionieren.
 * Die Bilder landen in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<UmgangApp>()

    private fun start(data: AppData, state: RepoState = RepoState.Ready, dark: Boolean = false) {
        app.repository.prefs.update { it.copy(dynamicColor = false, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        app.repository.seedForTest(data, state)
        compose.mainClock.autoAdvance = false
        compose.setContent { AppRoot(app, locked = false, onUnlock = {}, pendingRoute = null, onRouteConsumed = {}) }
        settle()
    }

    /** Endlos-Animationen (Expressive-Indikatoren) verhindern "idle" – daher Uhr manuell vorstellen. */
    private fun settle() {
        repeat(4) {
            compose.mainClock.advanceTimeBy(600)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
    }

    /** Klick über die Semantik-Aktion (robust bei angehaltener Test-Uhr). */
    private fun tap(text: String) {
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun shot(name: String) {
        settle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun mainScreens() {
        start(SampleData.data)
        shot("01_home")
        tap("Umgänge")
        shot("02_visits")
        tap("Vergangen (4)")
        shot("03_visits_past")
        tap("Abgesagt vom Vater")
        shot("04_visit_cancelled")
        tap("Absage bearbeiten")
        shot("05_cancel_sheet")
    }

    @Test
    fun journalAndEntry() {
        start(SampleData.data)
        tap("Tagebuch")
        shot("06_journal")
        tap("Umgang am")
        shot("07_entry_detail")
    }

    @Test
    fun wizard() {
        start(SampleData.data)
        tap("Bericht starten")
        shot("08_wizard_1")
        tap("Los geht's")
        shot("09_wizard_2")
        tap("Weiter")
        shot("10_wizard_3")
    }

    @Test
    fun moreAndReport() {
        start(SampleData.data)
        tap("Mehr")
        shot("11_more")
        tap("PDF-Bericht erstellen")
        shot("12_report")
    }

    @Test
    fun darkHome() {
        start(SampleData.data, dark = true)
        shot("13_home_dark")
    }

    @Test
    fun onboarding() {
        start(AppData(), RepoState.NeedsSetup)
        shot("14_onboarding_welcome")
        tap("Los geht's")
        shot("15_onboarding_folder")
    }

    @Test
    fun emptyState() {
        start(AppData(profile = SampleData.profile))
        shot("16_home_empty")
    }

    @Test
    fun recordings() {
        start(SampleData.data)
        tap("Aufnahme")
        shot("17_recordings")
        tap("Übergabe am Bahnhof")
        shot("18_recording_detail")
    }
}
