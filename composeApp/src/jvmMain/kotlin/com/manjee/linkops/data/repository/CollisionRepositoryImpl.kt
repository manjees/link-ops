package com.manjee.linkops.data.repository

import com.manjee.linkops.data.parser.IntentFilterParser
import com.manjee.linkops.domain.model.CollisionGroup
import com.manjee.linkops.domain.model.CollisionReport
import com.manjee.linkops.domain.model.CollisionSeverity
import com.manjee.linkops.domain.model.SchemeRegistration
import com.manjee.linkops.domain.repository.CollisionRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor

/**
 * Implementation of CollisionRepository using ADB commands
 *
 * Scans third-party installed apps, parses their intent filters,
 * and detects URI scheme collisions.
 */
class CollisionRepositoryImpl(
    private val adbExecutor: AdbShellExecutor,
    private val intentFilterParser: IntentFilterParser
) : CollisionRepository {

    override suspend fun detectCollisions(deviceSerial: String): Result<CollisionReport> {
        return runCatching {
            // Get third-party packages
            val packagesOutput = adbExecutor.executeOnDevice(
                deviceSerial,
                "pm list packages -3"
            ).getOrElse { error ->
                return Result.failure(error)
            }

            val packages = packagesOutput.lines()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotBlank() }

            // Collect all scheme registrations
            val allRegistrations = mutableListOf<SchemeRegistration>()

            for (packageName in packages) {
                val dumpsysOutput = adbExecutor.executeOnDevice(
                    deviceSerial,
                    "dumpsys package $packageName"
                ).getOrNull() ?: continue

                val registrations = intentFilterParser.parse(packageName, dumpsysOutput)
                allRegistrations.addAll(registrations)
            }

            // Group by collision key (scheme://host) and find collisions
            val collisionGroups = buildCollisionGroups(allRegistrations)

            CollisionReport(
                collisionGroups = collisionGroups,
                totalRegistrations = allRegistrations.size,
                scannedPackages = packages.size
            )
        }
    }

    private fun buildCollisionGroups(registrations: List<SchemeRegistration>): List<CollisionGroup> {
        // Group by collision key (scheme://host)
        val grouped = registrations.groupBy { it.collisionKey }

        return grouped.mapNotNull { (pattern, regs) ->
            // Only consider groups with multiple distinct packages
            val distinctPackages = regs.map { it.packageName }.distinct()
            if (distinctPackages.size < 2) return@mapNotNull null

            // Determine severity: exact scheme+host+path match = CRITICAL, otherwise WARNING
            val severity = determineSeverity(regs)

            CollisionGroup(
                pattern = pattern,
                severity = severity,
                registrations = regs
            )
        }.sortedWith(
            compareBy<CollisionGroup> { it.severity.ordinal }
                .thenByDescending { it.appCount }
        )
    }

    private fun determineSeverity(registrations: List<SchemeRegistration>): CollisionSeverity {
        // Group by full URI pattern to check for exact matches
        val byFullPattern = registrations.groupBy { it.uriPattern }
        val hasExactMatch = byFullPattern.any { (_, regs) ->
            regs.map { it.packageName }.distinct().size > 1
        }

        return if (hasExactMatch) CollisionSeverity.CRITICAL else CollisionSeverity.WARNING
    }
}
