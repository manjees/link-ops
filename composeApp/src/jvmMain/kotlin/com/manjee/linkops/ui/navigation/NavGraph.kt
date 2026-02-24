package com.manjee.linkops.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Navigation controller for desktop application
 *
 * Since Compose Desktop doesn't have built-in navigation like Android,
 * we implement a simple navigation system using state management.
 */
class NavigationController {
    private var _currentScreen by mutableStateOf<Screen>(Screen.Dashboard)
    val currentScreen: Screen get() = _currentScreen

    private val _backStack = mutableStateListOf<Screen>()
    val canNavigateBack: Boolean get() = _backStack.isNotEmpty()

    /**
     * Navigate to a new screen
     */
    fun navigateTo(screen: Screen) {
        _backStack.add(_currentScreen)
        _currentScreen = screen
    }

    /**
     * Navigate back to the previous screen
     * @return true if navigation was successful, false if back stack is empty
     */
    fun navigateBack(): Boolean {
        return if (_backStack.isNotEmpty()) {
            _currentScreen = _backStack.removeLast()
            true
        } else {
            false
        }
    }

    /**
     * Navigate to a screen and clear the back stack
     */
    fun navigateAndClearStack(screen: Screen) {
        _backStack.clear()
        _currentScreen = screen
    }

    /**
     * Pop to the root screen
     */
    fun popToRoot() {
        if (_backStack.isNotEmpty()) {
            _currentScreen = _backStack.first()
            _backStack.clear()
        }
    }
}

/**
 * Remember and create a navigation controller
 */
@Composable
fun rememberNavigationController(): NavigationController {
    return remember { NavigationController() }
}

/**
 * Navigation host that displays content based on current screen
 *
 * @param navController Navigation controller
 * @param content Content builder for each screen
 */
@Composable
fun NavHost(
    navController: NavigationController,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = navController.currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            content(screen)
        }
    }
}

/**
 * Composable local for accessing navigation controller
 */
val LocalNavigationController = compositionLocalOf<NavigationController> {
    error("No NavigationController provided")
}

/**
 * Provides navigation controller to composable tree
 */
@Composable
fun ProvideNavigationController(
    navController: NavigationController = rememberNavigationController(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalNavigationController provides navController,
        content = content
    )
}

/**
 * Extension function to navigate with type safety
 */
fun NavigationController.navigateToDashboard() = navigateTo(Screen.Dashboard)
fun NavigationController.navigateToDeviceSelection() = navigateTo(Screen.DeviceSelection)
fun NavigationController.navigateToDiagnostics() = navigateTo(Screen.Diagnostics)
fun NavigationController.navigateToVerificationDeepDive() = navigateTo(Screen.VerificationDeepDive)
fun NavigationController.navigateToLogStream() = navigateTo(Screen.LogStream)
fun NavigationController.navigateToBatchTest() = navigateTo(Screen.BatchTest)
fun NavigationController.navigateToLocalHosting() = navigateTo(Screen.LocalHosting)
fun NavigationController.navigateToIntentSniffer() = navigateTo(Screen.IntentSniffer)
fun NavigationController.navigateToSettings() = navigateTo(Screen.Settings)
