package com.manjee.linkops.domain.model

/**
 * Represents a single URI scheme registration from an app's intent filter
 */
data class SchemeRegistration(
    val packageName: String,
    val activityName: String,
    val scheme: String,
    val host: String?,
    val path: String?,
    val pathPrefix: String?,
    val pathPattern: String?,
    val autoVerify: Boolean = false
) {
    /**
     * URI pattern combining scheme, host, and path info
     */
    val uriPattern: String
        get() {
            val sb = StringBuilder(scheme).append("://")
            host?.let { sb.append(it) } ?: sb.append("*")

            when {
                path != null -> sb.append(path)
                pathPrefix != null -> sb.append(pathPrefix).append("*")
                pathPattern != null -> sb.append(pathPattern)
                else -> sb.append("/*")
            }

            return sb.toString()
        }

    /**
     * Key for grouping collisions — scheme + host combination
     */
    val collisionKey: String
        get() = "$scheme://${host ?: "*"}"
}

/**
 * Severity level for a collision group
 */
enum class CollisionSeverity {
    CRITICAL,   // Exact scheme+host match across apps
    WARNING;    // Partial overlap (same scheme, different host/path patterns)

    val displayName: String
        get() = when (this) {
            CRITICAL -> "Critical"
            WARNING -> "Warning"
        }
}

/**
 * A group of apps that share the same URI scheme+host pattern
 */
data class CollisionGroup(
    val pattern: String,
    val severity: CollisionSeverity,
    val registrations: List<SchemeRegistration>
) {
    /**
     * Number of distinct apps in this collision group
     */
    val appCount: Int
        get() = registrations.map { it.packageName }.distinct().size

    /**
     * List of distinct package names involved in the collision
     */
    val packageNames: List<String>
        get() = registrations.map { it.packageName }.distinct()

    /**
     * Whether any registration has autoVerify enabled
     */
    val hasVerifiedApp: Boolean
        get() = registrations.any { it.autoVerify }
}

/**
 * Complete collision detection result
 */
data class CollisionReport(
    val collisionGroups: List<CollisionGroup>,
    val totalRegistrations: Int,
    val scannedPackages: Int
) {
    /**
     * Number of critical collisions
     */
    val criticalCount: Int
        get() = collisionGroups.count { it.severity == CollisionSeverity.CRITICAL }

    /**
     * Number of warning collisions
     */
    val warningCount: Int
        get() = collisionGroups.count { it.severity == CollisionSeverity.WARNING }

    /**
     * Whether any collisions were detected
     */
    val hasCollisions: Boolean
        get() = collisionGroups.isNotEmpty()
}
