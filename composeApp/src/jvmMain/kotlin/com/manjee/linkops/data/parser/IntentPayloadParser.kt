package com.manjee.linkops.data.parser

import com.manjee.linkops.domain.model.BundleExtra
import com.manjee.linkops.domain.model.BundleExtraType
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.ParsingMethod

/**
 * Parser for `dumpsys activity activities` output to extract intent payload
 *
 * Uses a two-stage parsing strategy:
 * - Stage 1 (Regex): Extracts intent fields using regex patterns
 * - Stage 2 (Simple Split): Fallback when regex fails
 *
 * Example output:
 * ```
 * intent={act=android.intent.action.VIEW dat=https://example.com/path
 *         flg=0x10000000 cmp=com.example/.MainActivity (has extras)}
 *   Extras:
 *     referrer = "com.android.shell" (String)
 *     extra_id = 42 (Integer)
 * ```
 */
class IntentPayloadParser {

    private val intentBlockRegex = Regex("""intent=\{(.+?)\}""", RegexOption.DOT_MATCHES_ALL)
    private val actionRegex = Regex("""act=(\S+)""")
    private val dataRegex = Regex("""dat=(\S+)""")
    private val flagsRegex = Regex("""flg=0x([0-9a-fA-F]+)""")
    private val componentRegex = Regex("""cmp=(\S+?)(?:\s|\}|$)""")
    private val categoriesRegex = Regex("""cat=\[([^\]]*)\]""")
    private val extrasLineRegex = Regex("""^\s+(\S+)\s*=\s*(.+?)\s*\((\w+)\)\s*$""")

    /**
     * Parses dumpsys activity output to extract intent payload
     *
     * @param dumpsysOutput Raw output from `dumpsys activity activities`
     * @return IntentPayload with parsed fields, or raw-only payload if parsing fails
     */
    fun parse(dumpsysOutput: String): IntentPayload {
        if (dumpsysOutput.isBlank()) {
            return createRawPayload(dumpsysOutput)
        }

        // Stage 1: Try regex parsing
        val regexResult = parseWithRegex(dumpsysOutput)
        if (regexResult != null) return regexResult

        // Stage 2: Fallback to simple split parsing
        val splitResult = parseWithSplit(dumpsysOutput)
        if (splitResult != null) return splitResult

        // Stage 3: Raw only
        return createRawPayload(dumpsysOutput)
    }

    private fun parseWithRegex(output: String): IntentPayload? {
        val intentMatch = intentBlockRegex.find(output) ?: return null
        val intentContent = intentMatch.groupValues[1]

        val action = actionRegex.find(intentContent)?.groupValues?.get(1)
        val dataUri = dataRegex.find(intentContent)?.groupValues?.get(1)
        val flags = flagsRegex.find(intentContent)?.groupValues?.get(1)
            ?.toIntOrNull(16) ?: 0
        val component = componentRegex.find(intentContent)?.groupValues?.get(1)
            ?.removeSuffix("}")
        val categories = categoriesRegex.find(intentContent)?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val extras = parseExtras(output)

        return IntentPayload(
            action = action,
            dataUri = dataUri,
            categories = categories,
            flags = flags,
            component = component,
            extras = extras,
            rawOutput = output,
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.REGEX
        )
    }

    private fun parseWithSplit(output: String): IntentPayload? {
        val lines = output.lines()
        var action: String? = null
        var dataUri: String? = null
        var flags = 0
        var component: String? = null
        val categories = mutableListOf<String>()
        var foundIntent = false

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.contains("intent={") || trimmed.contains("Intent {")) {
                foundIntent = true
                val parts = trimmed.split(" ")
                for (part in parts) {
                    when {
                        part.startsWith("act=") -> action = part.removePrefix("act=")
                        part.startsWith("dat=") -> dataUri = part.removePrefix("dat=")
                        part.startsWith("flg=0x") -> {
                            flags = part.removePrefix("flg=0x")
                                .trimEnd('}', ' ')
                                .toIntOrNull(16) ?: 0
                        }
                        part.startsWith("cmp=") -> {
                            component = part.removePrefix("cmp=").trimEnd('}', ' ')
                        }
                        part.startsWith("cat=") -> {
                            val catContent = part.removePrefix("cat=")
                                .removePrefix("[")
                                .removeSuffix("]")
                                .trim()
                            if (catContent.isNotEmpty()) {
                                categories.addAll(catContent.split(",").map { it.trim() })
                            }
                        }
                    }
                }
            }
        }

        if (!foundIntent) return null

        val extras = parseExtras(output)

        return IntentPayload(
            action = action,
            dataUri = dataUri,
            categories = categories,
            flags = flags,
            component = component,
            extras = extras,
            rawOutput = output,
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.SIMPLE_SPLIT
        )
    }

    private fun parseExtras(output: String): Map<String, BundleExtra> {
        val extras = mutableMapOf<String, BundleExtra>()
        val lines = output.lines()
        var inExtrasSection = false

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("Extras:") || trimmed.startsWith("extras:") ||
                trimmed.contains("Bundle[{")
            ) {
                inExtrasSection = true
                // Handle inline Bundle[{key=value, key=value}]
                if (trimmed.contains("Bundle[{")) {
                    parseBundleInline(trimmed, extras)
                    inExtrasSection = false
                }
                continue
            }

            if (inExtrasSection) {
                // Exit extras section when encountering non-extra content
                if (trimmed.isEmpty() || trimmed.startsWith("intent=") ||
                    (!trimmed.contains("=") && !trimmed.matches(Regex("""^\s+\S+\s*=.*""")))
                ) {
                    inExtrasSection = false
                    continue
                }

                val match = extrasLineRegex.matchEntire(line)
                if (match != null) {
                    val key = match.groupValues[1]
                    val value = match.groupValues[2].trim().removeSurrounding("\"")
                    val typeStr = match.groupValues[3]
                    val type = parseExtraType(typeStr)
                    extras[key] = BundleExtra(key = key, value = value, type = type)
                }
            }
        }

        return extras
    }

    private fun parseBundleInline(line: String, extras: MutableMap<String, BundleExtra>) {
        val bundleContent = line.substringAfter("Bundle[{").substringBefore("}]")
        if (bundleContent.isBlank()) return

        bundleContent.split(",").forEach { pair ->
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                extras[key] = BundleExtra(key = key, value = value, type = BundleExtraType.UNKNOWN)
            }
        }
    }

    private fun parseExtraType(typeString: String): BundleExtraType {
        return when (typeString.lowercase()) {
            "string", "java.lang.string" -> BundleExtraType.STRING
            "int", "integer", "java.lang.integer" -> BundleExtraType.INT
            "long", "java.lang.long" -> BundleExtraType.LONG
            "float", "java.lang.float" -> BundleExtraType.FLOAT
            "boolean", "java.lang.boolean" -> BundleExtraType.BOOLEAN
            "bundle", "android.os.bundle" -> BundleExtraType.BUNDLE
            "parcelable" -> BundleExtraType.PARCELABLE
            else -> BundleExtraType.UNKNOWN
        }
    }

    private fun createRawPayload(output: String): IntentPayload {
        return IntentPayload(
            action = null,
            dataUri = null,
            categories = emptyList(),
            flags = 0,
            component = null,
            extras = emptyMap(),
            rawOutput = output,
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.RAW_ONLY
        )
    }
}
