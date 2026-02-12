package com.manjee.linkops.domain.model

/**
 * Represents a batch test scenario containing multiple deep link templates
 *
 * @param id Unique identifier for the scenario
 * @param name Human-readable scenario name
 * @param description Optional description of the test scenario
 * @param templates List of deep link templates to test
 * @param variables Map of variable names to their possible values for combinatorial testing
 * @param delayBetweenLaunchesMs Delay in milliseconds between sequential launches
 */
data class TestScenario(
    val id: String,
    val name: String,
    val description: String = "",
    val templates: List<DeepLinkTemplate> = emptyList(),
    val variables: Map<String, List<String>> = emptyMap(),
    val delayBetweenLaunchesMs: Long = DEFAULT_DELAY_MS
) {
    val totalLaunchCount: Int
        get() {
            if (templates.isEmpty()) return 0
            val combinationCount = if (variables.isEmpty()) 1
            else variables.values.fold(1) { acc, values -> acc * values.size.coerceAtLeast(1) }
            return templates.size * combinationCount
        }

    companion object {
        const val DEFAULT_DELAY_MS = 1000L
    }
}

/**
 * Represents a deep link URI template with optional metadata
 *
 * @param uri URI string, may contain {variable} placeholders
 * @param name Optional human-readable name for this template
 * @param expectedActivity Optional expected Activity class that should resolve
 */
data class DeepLinkTemplate(
    val uri: String,
    val name: String = "",
    val expectedActivity: String = ""
) {
    val hasPlaceholders: Boolean
        get() = PLACEHOLDER_REGEX.containsMatchIn(uri)

    val placeholderNames: Set<String>
        get() = PLACEHOLDER_REGEX.findAll(uri).map { it.groupValues[1] }.toSet()

    companion object {
        val PLACEHOLDER_REGEX = Regex("\\{([^}]+)}")
    }
}
