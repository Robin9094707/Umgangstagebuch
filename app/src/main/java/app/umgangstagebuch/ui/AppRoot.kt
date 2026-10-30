package app.umgangstagebuch.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.umgangstagebuch.UmgangApp
import app.umgangstagebuch.data.RepoState
import app.umgangstagebuch.ui.components.LocalApp
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.LocalSnackbar
import app.umgangstagebuch.ui.components.ShapeBadge
import app.umgangstagebuch.ui.home.HomeScreen
import app.umgangstagebuch.ui.journal.EntryDetailScreen
import app.umgangstagebuch.ui.journal.EntryEditorScreen
import app.umgangstagebuch.ui.journal.JournalScreen
import app.umgangstagebuch.ui.onboarding.OnboardingScreen
import app.umgangstagebuch.ui.settings.MoreScreen
import app.umgangstagebuch.ui.settings.ProfileScreen
import app.umgangstagebuch.ui.settings.ReportScreen
import app.umgangstagebuch.ui.theme.UmgangTheme
import app.umgangstagebuch.ui.visits.VisitDetailScreen
import app.umgangstagebuch.ui.visits.VisitEditScreen
import app.umgangstagebuch.ui.visits.VisitsScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val VISITS = "visits"
    const val JOURNAL = "journal"
    const val MORE = "more"
    const val PROFILE = "profile"
    const val REPORT = "report"
    fun visit(id: String) = "visit/$id"
    fun visitEdit(id: String? = null) = "visit_edit" + (id?.let { "?id=$it" } ?: "")
    fun entry(id: String) = "entry/$id"
    fun entryEdit(id: String? = null, visitId: String? = null, category: String? = null, wizard: Boolean = false): String {
        val params = listOfNotNull(id?.let { "id=$it" }, visitId?.let { "visitId=$it" }, category?.let { "category=$it" }, "wizard=$wizard")
        return "entry_edit?" + params.joinToString("&")
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Übersicht", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.VISITS, "Umgänge", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Tab(Routes.JOURNAL, "Tagebuch", Icons.Outlined.AutoStories, Icons.Filled.AutoStories),
    Tab(Routes.MORE, "Mehr", Icons.Outlined.Menu, Icons.Filled.Menu),
)

@Composable
fun AppRoot(app: UmgangApp, locked: Boolean, onUnlock: () -> Unit, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val repo = app.repository
    val settings by repo.prefs.settings.collectAsStateWithLifecycle()
    val state by repo.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.messages.collect { snackbar.showSnackbar(it) } }

    UmgangTheme(settings.themeMode, settings.dynamicColor) {
        CompositionLocalProvider(LocalApp provides app, LocalRepo provides repo, LocalSnackbar provides snackbar) {
            var onboarding by rememberSaveable { mutableStateOf(false) }
            if (state == RepoState.NeedsSetup) onboarding = true

            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                AnimatedContent(
                    targetState = when {
                        locked && state != RepoState.NeedsSetup && !onboarding -> "lock"
                        onboarding -> "onboarding"
                        state == RepoState.Loading -> "loading"
                        state == RepoState.FolderLost -> "lost"
                        else -> "main"
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "root",
                ) { screen ->
                    when (screen) {
                        "lock" -> LockScreen(onUnlock)
                        "onboarding" -> OnboardingScreen(onFinished = { onboarding = false })
                        "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        "lost" -> FolderLostScreen()
                        else -> MainScaffold(snackbar, pendingRoute, onRouteConsumed)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(snackbar: SnackbarHostState, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    LaunchedEffect(pendingRoute) {
        val r = pendingRoute ?: return@LaunchedEffect
        when {
            r.startsWith("report/") -> nav.navigate(Routes.entryEdit(visitId = r.removePrefix("report/"), category = "VISIT_REPORT", wizard = true))
            r.startsWith("visit/") -> nav.navigate(r)
            r == "entry_new" -> nav.navigate(Routes.entryEdit())
        }
        onRouteConsumed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar {
                    tabs.forEach { t ->
                        val selected = current == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn() + slideInVertically { it / 12 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutVertically { it / 12 } },
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.VISITS) { VisitsScreen(nav) }
            composable(Routes.JOURNAL) { JournalScreen(nav) }
            composable(Routes.MORE) { MoreScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.REPORT) { ReportScreen(nav) }
            composable("visit/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                VisitDetailScreen(it.arguments?.getString("id").orEmpty(), nav)
            }
            composable(
                "visit_edit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { VisitEditScreen(it.arguments?.getString("id"), nav) }
            composable("entry/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                EntryDetailScreen(it.arguments?.getString("id").orEmpty(), nav)
            }
            composable(
                "entry_edit?id={id}&visitId={visitId}&category={category}&wizard={wizard}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("visitId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("wizard") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                val a = it.arguments
                EntryEditorScreen(
                    id = a?.getString("id"),
                    visitId = a?.getString("visitId"),
                    category = a?.getString("category"),
                    wizard = a?.getBoolean("wizard") ?: false,
                    nav = nav,
                )
            }
        }
    }
}

fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeBadge(shape = MaterialShapes.Cookie12Sided.toShape(), size = 120.dp) { Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("Gesperrt", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Dein Tagebuch ist geschützt.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onUnlock, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) { Text("Entsperren") }
    }
}

@Composable
private fun FolderLostScreen() {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { repo.reconnect(uri).onFailure { error = it.message ?: "Der Ordner kann nicht verwendet werden." } }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeBadge(shape = MaterialShapes.Cookie9Sided.toShape(), size = 120.dp, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Speicherordner nicht erreichbar", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bitte wähle den Ordner, in dem dein Umgangstagebuch liegt, noch einmal aus. Deine Daten sind dort weiterhin gespeichert.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { picker.launch(repo.folder) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Ordner auswählen")
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
