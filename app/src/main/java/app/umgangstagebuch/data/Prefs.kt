package app.umgangstagebuch.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

data class Settings(
    val folderUri: Uri? = null,
    val onboardingDone: Boolean = false,
    val appLock: Boolean = false,
    val visitReminders: Boolean = true,
    val reminderHoursBefore: Int = 24,
    val afterVisitReminder: Boolean = true,
    val weeklyReminder: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** Kleine Geräteeinstellungen. Die eigentlichen Daten liegen im gewählten Ordner. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun read() = Settings(
        folderUri = sp.getString("folder", null)?.let(Uri::parse),
        onboardingDone = sp.getBoolean("onboardingDone", false),
        appLock = sp.getBoolean("appLock", false),
        visitReminders = sp.getBoolean("visitReminders", true),
        reminderHoursBefore = sp.getInt("reminderHoursBefore", 24),
        afterVisitReminder = sp.getBoolean("afterVisitReminder", true),
        weeklyReminder = sp.getBoolean("weeklyReminder", false),
        dynamicColor = sp.getBoolean("dynamicColor", true),
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("themeMode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        sp.edit {
            putString("folder", s.folderUri?.toString())
            putBoolean("onboardingDone", s.onboardingDone)
            putBoolean("appLock", s.appLock)
            putBoolean("visitReminders", s.visitReminders)
            putInt("reminderHoursBefore", s.reminderHoursBefore)
            putBoolean("afterVisitReminder", s.afterVisitReminder)
            putBoolean("weeklyReminder", s.weeklyReminder)
            putBoolean("dynamicColor", s.dynamicColor)
            putString("themeMode", s.themeMode.name)
        }
        _settings.value = s
    }
}
