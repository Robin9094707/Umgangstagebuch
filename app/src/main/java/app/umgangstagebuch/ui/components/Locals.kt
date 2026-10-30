package app.umgangstagebuch.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import app.umgangstagebuch.UmgangApp
import app.umgangstagebuch.data.Repository

val LocalApp = staticCompositionLocalOf<UmgangApp> { error("App fehlt") }
val LocalRepo = staticCompositionLocalOf<Repository> { error("Repository fehlt") }
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }
