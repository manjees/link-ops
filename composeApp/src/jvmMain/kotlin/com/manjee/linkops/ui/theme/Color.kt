package com.manjee.linkops.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * LinkOps Color Palette
 *
 * Professional color scheme for a developer tool
 * with clear status indicators
 */
object LinkOpsColors {
    // Primary colors
    val Primary = Color(0xFF2196F3)
    val PrimaryDark = Color(0xFF1976D2)
    val PrimaryLight = Color(0xFFBBDEFB)

    // Secondary colors
    val Secondary = Color(0xFF607D8B)
    val SecondaryDark = Color(0xFF455A64)
    val SecondaryLight = Color(0xFFCFD8DC)

    // Background colors
    val Background = Color(0xFFFAFAFA)
    val BackgroundDark = Color(0xFF121212)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFFF5F5F5)
    val SurfaceVariantDark = Color(0xFF2D2D2D)

    // Status colors
    val Success = Color(0xFF4CAF50)
    val SuccessLight = Color(0xFFE8F5E9)
    val Warning = Color(0xFFFF9800)
    val WarningLight = Color(0xFFFFF3E0)
    val Error = Color(0xFFF44336)
    val ErrorLight = Color(0xFFFFEBEE)
    val Info = Color(0xFF2196F3)
    val InfoLight = Color(0xFFE3F2FD)

    // Device state colors
    val DeviceOnline = Color(0xFF4CAF50)
    val DeviceOffline = Color(0xFF9E9E9E)
    val DeviceUnauthorized = Color(0xFFFF9800)

    // Verification state colors
    val Verified = Color(0xFF4CAF50)
    val Unverified = Color(0xFFFF9800)
    val Denied = Color(0xFFF44336)
    val Unknown = Color(0xFF9E9E9E)

    // Text colors
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF212121)
    val OnBackgroundDark = Color(0xFFE0E0E0)
    val OnSurface = Color(0xFF212121)
    val OnSurfaceDark = Color(0xFFE0E0E0)
    val OnSurfaceVariant = Color(0xFF757575)

    // Terminal/Log colors
    val TerminalBackground = Color(0xFF1E1E1E)
    val TerminalText = Color(0xFF00FF00)
    val TerminalError = Color(0xFFFF5252)
    val TerminalWarning = Color(0xFFFFD740)
    val TerminalInfo = Color(0xFF40C4FF)

    // Topology node colors
    val TopologySchemeHttp = Color(0xFF2196F3)
    val TopologySchemeCustom = Color(0xFF7C4DFF)
    val TopologyHostVerified = Color(0xFF4CAF50)
    val TopologyHostFailed = Color(0xFFF44336)
    val TopologyHostUnknown = Color(0xFF9E9E9E)
    val TopologyPath = Color(0xFF607D8B)
    val TopologyActivity = Color(0xFF78909C)

    // Topology insight severity backgrounds
    val InsightInfoBg = Color(0xFFE3F2FD)
    val InsightWarningBg = Color(0xFFFFF3E0)
    val InsightErrorBg = Color(0xFFFFEBEE)

    // Divider
    val Divider = Color(0xFFE0E0E0)
    val DividerDark = Color(0xFF424242)
}
