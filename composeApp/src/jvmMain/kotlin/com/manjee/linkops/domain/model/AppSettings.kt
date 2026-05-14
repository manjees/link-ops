package com.manjee.linkops.domain.model

/**
 * Persisted user preferences for LinkOps.
 *
 * Stored at `~/.linkops/settings.json`. Defaults match the values the app has been
 * shipping with so that existing users see no behavior change until they explicitly
 * change something.
 */
data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /**
     * Absolute path to an ADB binary the user wants to force.
     * When null, [AdbBinaryManager] falls back to the system PATH then the bundled binary.
     */
    val adbPathOverride: String? = null,
    val defaultHost: String = DEFAULT_HOST,
    val defaultPort: Int = DEFAULT_PORT,
    val devicePollingIntervalSeconds: Int = DEFAULT_POLLING_INTERVAL_SECONDS
) {
    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 8080
        const val DEFAULT_POLLING_INTERVAL_SECONDS: Int = 2

        const val MIN_PORT: Int = 1
        const val MAX_PORT: Int = 65535
        const val MIN_POLLING_INTERVAL_SECONDS: Int = 1
        const val MAX_POLLING_INTERVAL_SECONDS: Int = 60
    }
}

enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM
}
