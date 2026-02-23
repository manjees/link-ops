package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.SchemeRegistration

/**
 * Parser for extracting URI scheme registrations from `adb shell dumpsys package` output
 *
 * Extracts intent filters with VIEW action and BROWSABLE category to find
 * all URI scheme registrations for collision detection.
 *
 * Example output:
 * ```
 * Activity Resolver Table:
 *   Non-Data Actions:
 *     ...
 *   Schemes:
 *     http:
 *       3853b45 com.example.app/.MainActivity filter c6ebba8
 *         Action: "android.intent.action.VIEW"
 *         Category: "android.intent.category.DEFAULT"
 *         Category: "android.intent.category.BROWSABLE"
 *         AutoVerify=true
 *         Scheme: "http"
 *         Scheme: "https"
 *         Authority: "example.com": -1
 *         Path: "PatternMatcher{LITERAL: /path}"
 * ```
 */
class IntentFilterParser {

    /**
     * Parse dumpsys package output and extract scheme registrations
     *
     * @param packageName Package name being parsed
     * @param dumpsysOutput Raw output from `dumpsys package <packageName>`
     * @return List of scheme registrations found in the package
     */
    fun parse(packageName: String, dumpsysOutput: String): List<SchemeRegistration> {
        if (dumpsysOutput.isBlank()) return emptyList()
        if (dumpsysOutput.startsWith("error:") || dumpsysOutput.contains("Unable to find package")) {
            return emptyList()
        }

        val activitySection = extractActivitySection(dumpsysOutput) ?: return emptyList()
        val activityBlocks = parseActivityBlocks(activitySection)

        return activityBlocks.flatMap { (activityName, filterBlocks) ->
            filterBlocks.mapNotNull { filterBlock ->
                parseFilterBlock(packageName, activityName, filterBlock)
            }.flatten()
        }
    }

    private fun extractActivitySection(output: String): String? {
        val startIndex = output.indexOf("Activity Resolver Table:")
        if (startIndex == -1) return null

        val endMarkers = listOf("Receiver Resolver Table:", "Service Resolver Table:", "Provider Resolver Table:")
        var endIndex = output.length

        for (marker in endMarkers) {
            val idx = output.indexOf(marker, startIndex)
            if (idx != -1 && idx < endIndex) {
                endIndex = idx
            }
        }

        return output.substring(startIndex, endIndex)
    }

    private fun parseActivityBlocks(section: String): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        val lines = section.lines()

        var currentActivity: String? = null
        var currentFilter = StringBuilder()
        var inFilter = false

        for (line in lines) {
            val activityMatch = ACTIVITY_PATTERN.find(line)
            if (activityMatch != null) {
                if (currentActivity != null && currentFilter.isNotEmpty()) {
                    result.getOrPut(currentActivity) { mutableListOf() }.add(currentFilter.toString())
                }
                currentActivity = activityMatch.groupValues[1]
                currentFilter = StringBuilder()
                inFilter = true
                continue
            }

            if (inFilter && currentActivity != null) {
                if (line.isNotBlank() && !line.startsWith("          ") && !line.startsWith("\t")) {
                    if (ACTIVITY_PATTERN.containsMatchIn(line)) {
                        // Will be handled in next iteration
                    } else if (!line.startsWith("        ")) {
                        inFilter = false
                    }
                }
                if (inFilter) {
                    currentFilter.appendLine(line)
                }
            }
        }

        if (currentActivity != null && currentFilter.isNotEmpty()) {
            result.getOrPut(currentActivity) { mutableListOf() }.add(currentFilter.toString())
        }

        return result
    }

    private fun parseFilterBlock(
        packageName: String,
        activityName: String,
        filterBlock: String
    ): List<SchemeRegistration>? {
        val actions = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val schemes = mutableListOf<String>()
        val paths = mutableListOf<Triple<String?, String?, String?>>()
        var host: String? = null
        var autoVerify = false

        for (line in filterBlock.lines()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("Action:")) {
                val action = trimmed.removePrefix("Action:").trim().removeSurrounding("\"")
                if (action.isNotEmpty()) actions.add(action)
            }

            if (trimmed.startsWith("Category:")) {
                val category = trimmed.removePrefix("Category:").trim().removeSurrounding("\"")
                if (category.isNotEmpty()) categories.add(category)
            }

            if (trimmed.contains("AutoVerify=true", ignoreCase = true)) {
                autoVerify = true
            }

            if (trimmed.startsWith("Scheme:")) {
                val scheme = trimmed.removePrefix("Scheme:").trim().removeSurrounding("\"")
                if (scheme.isNotEmpty()) schemes.add(scheme)
            }

            if (trimmed.startsWith("Authority:")) {
                val authorityMatch = AUTHORITY_PATTERN.find(trimmed)
                if (authorityMatch != null) {
                    host = authorityMatch.groupValues[1]
                }
            }

            if (trimmed.startsWith("Path:")) {
                val pathMatch = PATH_PATTERN.find(trimmed)
                if (pathMatch != null) {
                    val pathType = pathMatch.groupValues[1]
                    val pathValue = pathMatch.groupValues[2].trim()
                    when (pathType.uppercase()) {
                        "LITERAL" -> paths.add(Triple(pathValue, null, null))
                        "PREFIX" -> paths.add(Triple(null, pathValue, null))
                        "GLOB", "ADVANCED_GLOB" -> paths.add(Triple(null, null, pathValue))
                    }
                }
            }

            if (trimmed.startsWith("PathPrefix:")) {
                val pathPrefix = trimmed.removePrefix("PathPrefix:").trim().removeSurrounding("\"")
                if (pathPrefix.isNotEmpty()) paths.add(Triple(null, pathPrefix, null))
            }

            if (trimmed.startsWith("PathPattern:")) {
                val pathPattern = trimmed.removePrefix("PathPattern:").trim().removeSurrounding("\"")
                if (pathPattern.isNotEmpty()) paths.add(Triple(null, null, pathPattern))
            }
        }

        // Only consider VIEW + BROWSABLE filters
        val isViewAction = actions.contains("android.intent.action.VIEW")
        val isBrowsable = categories.contains("android.intent.category.BROWSABLE")
        if (!isViewAction || !isBrowsable || schemes.isEmpty()) return null

        val registrations = mutableListOf<SchemeRegistration>()

        if (paths.isEmpty()) {
            for (scheme in schemes) {
                registrations.add(
                    SchemeRegistration(
                        packageName = packageName,
                        activityName = activityName,
                        scheme = scheme,
                        host = host,
                        path = null,
                        pathPrefix = null,
                        pathPattern = null,
                        autoVerify = autoVerify
                    )
                )
            }
        } else {
            for (scheme in schemes) {
                for ((path, pathPrefix, pathPattern) in paths) {
                    registrations.add(
                        SchemeRegistration(
                            packageName = packageName,
                            activityName = activityName,
                            scheme = scheme,
                            host = host,
                            path = path,
                            pathPrefix = pathPrefix,
                            pathPattern = pathPattern,
                            autoVerify = autoVerify
                        )
                    )
                }
            }
        }

        return registrations
    }

    companion object {
        private val ACTIVITY_PATTERN = Regex("""^\s+\w+\s+([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)\s+filter""")
        private val AUTHORITY_PATTERN = Regex(""""([^"]+)":\s*(-?\d+)""")
        private val PATH_PATTERN = Regex("""PatternMatcher\{(\w+):\s*([^}]+)\}""")
    }
}
