package com.manjee.linkops.data.strategy

/**
 * Strategy interface for Android version-specific ADB commands
 *
 * Android 12 (SDK 31) introduced new app links commands,
 * so we need different strategies for different OS versions.
 */
interface AdbCommandStrategy {
    /**
     * Gets the command to list app links
     * @param packageName Optional package filter
     * @return Shell command to execute
     */
    fun getAppLinksCommand(packageName: String? = null): String

    /**
     * Gets the command to force re-verification
     * @param packageName Package to re-verify
     * @return Shell command to execute
     */
    fun forceReverifyCommand(packageName: String): String

    /**
     * Gets the logcat filter for verification logs
     * @return Logcat filter arguments
     */
    fun getVerificationLogFilter(): String
}

/**
 * Strategy for Android 11 and below (SDK <= 30)
 */
class Android11Strategy : AdbCommandStrategy {

    override fun getAppLinksCommand(packageName: String?): String {
        // Note: package filtering is performed by DumpsysParser/caller in Kotlin.
        // We don't shell-pipe to `grep` because ProcessBuilder doesn't go through a shell —
        // `|` would be passed to `grep` as a literal argument and the command would fail.
        return "dumpsys package domain-preferred-apps"
    }

    override fun forceReverifyCommand(packageName: String): String {
        // Android 11: Reset app links state
        return "pm set-app-links --package $packageName 0 all"
    }

    override fun getVerificationLogFilter(): String {
        return "IntentFilterIntentOp:V SingleTaskInstance:V *:S"
    }
}

/**
 * Strategy for Android 12 and above (SDK >= 31)
 */
class Android12PlusStrategy : AdbCommandStrategy {

    override fun getAppLinksCommand(packageName: String?): String {
        return if (packageName != null) {
            "pm get-app-links $packageName"
        } else {
            "pm get-app-links"
        }
    }

    override fun forceReverifyCommand(packageName: String): String {
        // Android 12+: Trigger re-verification
        return "pm verify-app-links --re-verify $packageName"
    }

    override fun getVerificationLogFilter(): String {
        return "IntentFilterIntentOp:V DomainVerification:V *:S"
    }
}

/**
 * Factory for creating appropriate strategy based on SDK level
 */
class AdbCommandStrategyFactory {

    companion object {
        private const val ANDROID_12_SDK_LEVEL = 31
    }

    fun create(sdkLevel: Int): AdbCommandStrategy {
        return if (sdkLevel >= ANDROID_12_SDK_LEVEL) {
            Android12PlusStrategy()
        } else {
            Android11Strategy()
        }
    }
}
