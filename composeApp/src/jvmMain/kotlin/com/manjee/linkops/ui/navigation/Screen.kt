package com.manjee.linkops.ui.navigation

/**
 * Sealed class representing all screens in the application
 */
sealed class Screen(val route: String, val title: String) {
    /**
     * Main dashboard screen - shows device selection and app links
     */
    data object Dashboard : Screen("dashboard", "Dashboard")

    /**
     * Device selection screen - for choosing a connected device
     */
    data object DeviceSelection : Screen("device_selection", "Select Device")

    /**
     * App links detail screen - shows detailed app link information
     */
    data object AppLinksDetail : Screen("app_links_detail", "App Links") {
        fun createRoute(packageName: String): String = "app_links_detail/$packageName"
    }

    /**
     * Diagnostics screen - for assetlinks.json validation
     */
    data object Diagnostics : Screen("diagnostics", "Diagnostics")

    /**
     * Manifest analyzer screen - for analyzing app manifests and deep links
     */
    data object ManifestAnalyzer : Screen("manifest_analyzer", "Manifest Analyzer")

    /**
     * Verification deep dive screen - for deep verification analysis
     */
    data object VerificationDeepDive : Screen("verification_deep_dive", "Verification Deep Dive")

    /**
     * Log streamer screen - for real-time logcat streaming with deep link filtering
     */
    data object LogStream : Screen("log_stream", "Log Streamer")

    /**
     * Batch test screen - for batch deep link testing with scenario management
     */
    data object BatchTest : Screen("batch_test", "Batch Test")

    /**
     * Settings screen
     */
    data object Settings : Screen("settings", "Settings")

    companion object {
        /**
         * Get all main screens for navigation
         */
        fun mainScreens(): List<Screen> = listOf(
            Dashboard,
            Diagnostics,
            VerificationDeepDive,
            ManifestAnalyzer,
            LogStream,
            BatchTest,
            Settings
        )
    }
}

/**
 * Navigation state holder
 */
data class NavigationState(
    val currentScreen: Screen = Screen.Dashboard,
    val backStack: List<Screen> = emptyList()
) {
    fun navigateTo(screen: Screen): NavigationState {
        return copy(
            currentScreen = screen,
            backStack = backStack + currentScreen
        )
    }

    fun navigateBack(): NavigationState? {
        return if (backStack.isNotEmpty()) {
            copy(
                currentScreen = backStack.last(),
                backStack = backStack.dropLast(1)
            )
        } else {
            null
        }
    }

    val canNavigateBack: Boolean
        get() = backStack.isNotEmpty()
}
