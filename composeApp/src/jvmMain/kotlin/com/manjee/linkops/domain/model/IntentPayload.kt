package com.manjee.linkops.domain.model

/**
 * Represents a captured intent payload from an Android activity
 *
 * Contains all extracted fields from `dumpsys activity activities` output
 * including action, data URI, flags, component, and extras bundle.
 */
data class IntentPayload(
    val action: String?,
    val dataUri: String?,
    val categories: List<String>,
    val flags: Int,
    val component: String?,
    val extras: Map<String, BundleExtra>,
    val rawOutput: String,
    val timestamp: Long,
    val parsingMethod: ParsingMethod
) {
    val displayName: String
        get() = dataUri ?: action ?: "Unknown Intent"

    val hasExtras: Boolean
        get() = extras.isNotEmpty()
}

/**
 * Represents a single key-value pair from an intent's extras Bundle
 */
data class BundleExtra(
    val key: String,
    val value: String?,
    val type: BundleExtraType
)

/**
 * Supported Bundle extra value types
 */
enum class BundleExtraType {
    STRING, INT, LONG, FLOAT, BOOLEAN, BUNDLE, PARCELABLE, UNKNOWN
}

/**
 * Result of comparing two intent payloads
 */
data class PayloadComparison(
    val addedExtras: Map<String, BundleExtra>,
    val removedExtras: Map<String, BundleExtra>,
    val changedExtras: Map<String, Pair<BundleExtra, BundleExtra>>,
    val unchangedExtras: Map<String, BundleExtra>
)

/**
 * Indicates which parsing strategy was used to extract intent data
 */
enum class ParsingMethod {
    REGEX, SIMPLE_SPLIT, RAW_ONLY
}

/**
 * Event emitted when an intent is fired from the main screen
 *
 * @param deviceSerial Serial number of the target device
 * @param uri The deep link URI that was fired
 */
data class IntentFiredEvent(
    val deviceSerial: String,
    val uri: String
)
