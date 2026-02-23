package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.DeepLinkTemplate

/**
 * UseCase for resolving deep link template URIs with variable substitution
 */
class ResolveTemplateUrisUseCase(
    private val resolver: TemplateUriResolver
) {
    /**
     * Resolves all template URIs with the given variable values
     *
     * @param templates List of deep link templates
     * @param variables Map of variable names to their possible values
     * @return List of fully resolved URI strings
     */
    operator fun invoke(
        templates: List<DeepLinkTemplate>,
        variables: Map<String, List<String>>
    ): List<String> {
        return resolver.resolveAll(templates, variables)
    }
}

/**
 * Interface for resolving template URIs with variable substitution
 */
interface TemplateUriResolver {
    /**
     * Resolves all URIs from templates and variable values
     *
     * @param templates List of deep link templates
     * @param variables Map of variable names to their possible values
     * @return List of fully resolved URI strings
     */
    fun resolveAll(
        templates: List<DeepLinkTemplate>,
        variables: Map<String, List<String>>
    ): List<String>
}
