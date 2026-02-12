package com.manjee.linkops.data.mapper

import com.manjee.linkops.domain.model.DeepLinkTemplate
import com.manjee.linkops.domain.usecase.batchtest.TemplateUriResolver

/**
 * Handles variable substitution in deep link URI templates
 *
 * Replaces `{variable}` placeholders with concrete values.
 * Supports combinatorial expansion when multiple variables have multiple values.
 */
class ParameterSubstituter : TemplateUriResolver {

    /**
     * Resolves all URIs from a list of templates and variable values
     *
     * @param templates List of deep link templates
     * @param variables Map of variable names to their possible values
     * @return List of fully resolved URI strings
     */
    override fun resolveAll(
        templates: List<DeepLinkTemplate>,
        variables: Map<String, List<String>>
    ): List<String> {
        if (templates.isEmpty()) return emptyList()

        val combinations = generateCombinations(variables)

        return templates.flatMap { template ->
            if (!template.hasPlaceholders || combinations.isEmpty()) {
                listOf(template.uri)
            } else {
                combinations.map { combination -> substitute(template.uri, combination) }
            }
        }
    }

    /**
     * Substitutes placeholders in a URI template with values
     *
     * @param uriTemplate URI with {variable} placeholders
     * @param values Map of variable name to concrete value
     * @return Resolved URI string
     */
    fun substitute(uriTemplate: String, values: Map<String, String>): String {
        var result = uriTemplate
        values.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }

    /**
     * Generates Cartesian product of variable values
     *
     * @param variables Map of variable names to their possible values
     * @return List of all possible variable combinations
     */
    fun generateCombinations(
        variables: Map<String, List<String>>
    ): List<Map<String, String>> {
        if (variables.isEmpty()) return emptyList()

        val entries = variables.entries.toList()
        val nonEmpty = entries.filter { it.value.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()

        return nonEmpty.fold(listOf(emptyMap())) { acc, (key, values) ->
            acc.flatMap { existing ->
                values.map { value -> existing + (key to value) }
            }
        }
    }
}
