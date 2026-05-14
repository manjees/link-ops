package com.manjee.linkops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.ShortcutAction
import com.manjee.linkops.domain.model.ThemePreference
import com.manjee.linkops.ui.component.KeyboardShortcutHandler
import com.manjee.linkops.ui.component.ShortcutsHelpDialog
import com.manjee.linkops.ui.navigation.*
import com.manjee.linkops.ui.screen.diagnostics.DiagnosticsScreen
import com.manjee.linkops.ui.screen.diagnostics.DiagnosticsViewModel
import com.manjee.linkops.ui.screen.diagnostics.VerificationDeepDiveScreen
import com.manjee.linkops.ui.screen.diagnostics.VerificationDeepDiveViewModel
import com.manjee.linkops.ui.screen.logstream.LogStreamScreen
import com.manjee.linkops.ui.screen.logstream.LogStreamViewModel
import com.manjee.linkops.ui.screen.main.MainScreen
import com.manjee.linkops.ui.screen.main.MainViewModel
import com.manjee.linkops.ui.screen.batchtest.BatchTestScreen
import com.manjee.linkops.ui.screen.batchtest.BatchTestViewModel
import com.manjee.linkops.ui.screen.localhosting.LocalHostingScreen
import com.manjee.linkops.ui.screen.localhosting.LocalHostingViewModel
import com.manjee.linkops.ui.screen.manifest.ManifestAnalyzerScreen
import com.manjee.linkops.ui.screen.manifest.ManifestAnalyzerViewModel
import com.manjee.linkops.ui.screen.settings.SettingsScreen
import com.manjee.linkops.ui.screen.settings.SettingsViewModel
import com.manjee.linkops.ui.screen.sniffer.IntentSnifferScreen
import com.manjee.linkops.ui.screen.sniffer.IntentSnifferViewModel
import com.manjee.linkops.ui.theme.LinkOpsTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * CompositionLocal providing a focus-search event counter.
 * Screens observe this counter and request focus on their search field when it increments.
 */
val LocalSearchFocusTrigger = compositionLocalOf { mutableStateOf(0) }

/**
 * Main Application Composable
 *
 * Entry point for the LinkOps desktop application.
 * Uses the new UI structure with:
 * - LinkOpsTheme for consistent styling
 * - NavigationController for screen navigation
 * - Sidebar navigation for main screens
 * - Global keyboard shortcuts
 */
@Composable
@Preview
fun App() {
    val navController = rememberNavigationController()
    val mainViewModel = remember { MainViewModel() }
    val diagnosticsViewModel = remember { DiagnosticsViewModel() }
    val manifestAnalyzerViewModel = remember { ManifestAnalyzerViewModel() }
    val verificationDeepDiveViewModel = remember { VerificationDeepDiveViewModel() }
    val logStreamViewModel = remember { LogStreamViewModel() }
    val batchTestViewModel = remember { BatchTestViewModel() }
    val localHostingViewModel = remember { LocalHostingViewModel() }
    val intentSnifferViewModel = remember { IntentSnifferViewModel() }
    val settingsViewModel = remember { SettingsViewModel() }
    val keyboardShortcutHandler = remember { KeyboardShortcutHandler() }
    val searchFocusTrigger = remember { mutableStateOf(0) }

    var showShortcutsDialog by remember { mutableStateOf(false) }

    // Cleanup ViewModels when composable leaves composition.
    // The Ktor local server must be stopped synchronously BEFORE the ViewModel's
    // viewModelScope is cancelled — otherwise stopServer() never runs and the
    // port stays bound until the OS reclaims it on process exit.
    DisposableEffect(Unit) {
        onDispose {
            runBlocking {
                runCatching { AppContainer.stopLocalServerUseCase() }
            }
            mainViewModel.onCleared()
            diagnosticsViewModel.onCleared()
            manifestAnalyzerViewModel.onCleared()
            verificationDeepDiveViewModel.onCleared()
            logStreamViewModel.onCleared()
            batchTestViewModel.onCleared()
            localHostingViewModel.onCleared()
            intentSnifferViewModel.onCleared()
            settingsViewModel.onCleared()
        }
    }

    val settingsState by settingsViewModel.uiState.collectAsState()
    val systemInDark = isSystemInDarkTheme()
    val darkTheme = when (settingsState.draft.theme) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> systemInDark
    }

    LinkOpsTheme(darkTheme = darkTheme) {
        ProvideNavigationController(navController) {
            CompositionLocalProvider(
                LocalSearchFocusTrigger provides searchFocusTrigger
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            when (keyboardShortcutHandler.handleKeyEvent(event)) {
                                ShortcutAction.REFRESH_DEVICES -> {
                                    mainViewModel.refreshDevices()
                                    true
                                }

                                ShortcutAction.FOCUS_SEARCH -> {
                                    searchFocusTrigger.value++
                                    true
                                }

                                ShortcutAction.CLOSE_PANEL -> {
                                    navController.navigateBack()
                                    true
                                }

                                ShortcutAction.SHOW_SHORTCUTS_HELP -> {
                                    showShortcutsDialog = true
                                    true
                                }

                                null -> false
                            }
                        }
                ) {
                    // Sidebar Navigation
                    NavigationSidebar(
                        currentScreen = navController.currentScreen,
                        onNavigate = { screen -> navController.navigateTo(screen) }
                    )

                    // Main Content
                    NavHost(
                        navController = navController,
                        modifier = Modifier.weight(1f)
                    ) { screen ->
                        when (screen) {
                            Screen.Dashboard -> {
                                MainScreen(viewModel = mainViewModel)
                            }

                            Screen.DeviceSelection -> {
                                MainScreen(viewModel = mainViewModel)
                            }

                            Screen.Diagnostics -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                DiagnosticsScreen(
                                    viewModel = diagnosticsViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.ManifestAnalyzer -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                ManifestAnalyzerScreen(
                                    viewModel = manifestAnalyzerViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.VerificationDeepDive -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                VerificationDeepDiveScreen(
                                    viewModel = verificationDeepDiveViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.LogStream -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                LogStreamScreen(
                                    viewModel = logStreamViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.BatchTest -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                BatchTestScreen(
                                    viewModel = batchTestViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.LocalHosting -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                LocalHostingScreen(
                                    viewModel = localHostingViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.IntentSniffer -> {
                                val mainUiState by mainViewModel.uiState.collectAsState()
                                IntentSnifferScreen(
                                    viewModel = intentSnifferViewModel,
                                    devices = mainUiState.devices
                                )
                            }

                            Screen.Settings -> {
                                SettingsScreen(viewModel = settingsViewModel)
                            }

                            is Screen.AppLinksDetail -> {
                                MainScreen(viewModel = mainViewModel)
                            }
                        }
                    }
                }

                // Shortcuts help dialog
                if (showShortcutsDialog) {
                    ShortcutsHelpDialog(
                        shortcuts = KeyboardShortcutHandler.allShortcuts(),
                        onDismiss = { showShortcutsDialog = false }
                    )
                }
            }
        }
    }
}

/**
 * Navigation sidebar with icons for main screens
 */
@Composable
private fun NavigationSidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        NavigationRailItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            selected = currentScreen == Screen.Dashboard,
            onClick = { onNavigate(Screen.Dashboard) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.Search, contentDescription = "Diagnostics") },
            label = { Text("Diagnostics") },
            selected = currentScreen == Screen.Diagnostics,
            onClick = { onNavigate(Screen.Diagnostics) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = "Deep Dive") },
            label = { Text("Deep Dive") },
            selected = currentScreen == Screen.VerificationDeepDive,
            onClick = { onNavigate(Screen.VerificationDeepDive) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.Description, contentDescription = "Manifest") },
            label = { Text("Manifest") },
            selected = currentScreen == Screen.ManifestAnalyzer,
            onClick = { onNavigate(Screen.ManifestAnalyzer) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.Terminal, contentDescription = "Log Streamer") },
            label = { Text("Logcat") },
            selected = currentScreen == Screen.LogStream,
            onClick = { onNavigate(Screen.LogStream) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Batch Test") },
            label = { Text("Batch Test") },
            selected = currentScreen == Screen.BatchTest,
            onClick = { onNavigate(Screen.BatchTest) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.Dns, contentDescription = "Local Host") },
            label = { Text("Local Host") },
            selected = currentScreen == Screen.LocalHosting,
            onClick = { onNavigate(Screen.LocalHosting) }
        )

        NavigationRailItem(
            icon = { Icon(Icons.Default.BugReport, contentDescription = "Sniffer") },
            label = { Text("Sniffer") },
            selected = currentScreen == Screen.IntentSniffer,
            onClick = { onNavigate(Screen.IntentSniffer) }
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationRailItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
