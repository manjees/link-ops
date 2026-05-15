package com.manjee.linkops.data.repository

import com.manjee.linkops.data.parser.AasaParser
import com.manjee.linkops.domain.model.AasaIssue
import com.manjee.linkops.domain.model.AasaStatus
import com.manjee.linkops.domain.model.AasaValidation
import com.manjee.linkops.domain.repository.AasaRepository
import com.manjee.linkops.infrastructure.network.AasaClient
import com.manjee.linkops.infrastructure.network.AasaResponse

/**
 * Validates `apple-app-site-association` files. Mirrors AssetLinksRepositoryImpl —
 * the issue list grows as we see warnings (wrong Content-Type, root fallback,
 * non-empty apps array, etc.) and the final status is the worst of what we found.
 */
class AasaRepositoryImpl(
    private val client: AasaClient,
    private val parser: AasaParser
) : AasaRepository {

    override suspend fun validateAasa(domain: String): Result<AasaValidation> = runCatching {
        when (val response = client.fetch(domain)) {
            is AasaResponse.Success -> handleSuccess(domain, response)
            is AasaResponse.NotFound -> AasaValidation(
                domain = domain,
                url = response.url,
                status = AasaStatus.NOT_FOUND,
                issues = listOf(
                    AasaIssue(
                        severity = AasaIssue.Severity.ERROR,
                        code = AasaIssue.AasaIssueCode.FILE_NOT_FOUND,
                        message = "AASA file not found at either /.well-known/ or root path",
                        details = "Last tried: ${response.url}"
                    )
                )
            )
            is AasaResponse.Redirect -> AasaValidation(
                domain = domain,
                url = response.originalUrl,
                status = AasaStatus.REDIRECT,
                issues = listOf(
                    AasaIssue(
                        severity = AasaIssue.Severity.ERROR,
                        code = AasaIssue.AasaIssueCode.REDIRECT_DETECTED,
                        message = "AASA was served via redirect",
                        details = "Apple does not follow redirects when fetching AASA. Redirect target: ${response.redirectUrl}"
                    )
                )
            )
            is AasaResponse.HttpError -> AasaValidation(
                domain = domain,
                url = response.url,
                status = AasaStatus.NETWORK_ERROR,
                issues = listOf(
                    AasaIssue(
                        severity = AasaIssue.Severity.ERROR,
                        code = AasaIssue.AasaIssueCode.NETWORK_ERROR,
                        message = "HTTP error: ${response.statusCode}",
                        details = response.message
                    )
                )
            )
            is AasaResponse.NetworkError -> {
                val code = when {
                    response.message.contains("timeout", ignoreCase = true) ->
                        AasaIssue.AasaIssueCode.NETWORK_TIMEOUT
                    response.message.contains("ssl", ignoreCase = true) ||
                        response.message.contains("certificate", ignoreCase = true) ->
                        AasaIssue.AasaIssueCode.SSL_ERROR
                    else -> AasaIssue.AasaIssueCode.NETWORK_ERROR
                }
                AasaValidation(
                    domain = domain,
                    url = response.url,
                    status = AasaStatus.NETWORK_ERROR,
                    issues = listOf(
                        AasaIssue(
                            severity = AasaIssue.Severity.ERROR,
                            code = code,
                            message = "Network error: ${response.message}"
                        )
                    )
                )
            }
        }
    }

    private fun handleSuccess(
        domain: String,
        response: AasaResponse.Success
    ): AasaValidation {
        val issues = mutableListOf<AasaIssue>()

        // AASA is allowed to be served as `application/json` OR `application/pkcs7-mime`
        // (Apple historically signed AASAs). Anything else is a warning.
        val isAcceptableContentType = response.contentType.let { ct ->
            ct.contains("application/json", ignoreCase = true) ||
                ct.contains("application/pkcs7-mime", ignoreCase = true)
        }
        if (!isAcceptableContentType) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.WARNING,
                    code = AasaIssue.AasaIssueCode.WRONG_CONTENT_TYPE,
                    message = "Content-Type is not application/json",
                    details = "Received: ${response.contentType}"
                )
            )
        }

        if (!response.fromWellKnown) {
            issues.add(
                AasaIssue(
                    severity = AasaIssue.Severity.WARNING,
                    code = AasaIssue.AasaIssueCode.ROOT_PATH_FALLBACK,
                    message = "AASA was found only at the legacy root path",
                    details = "iOS 13+ recommends serving it at /.well-known/apple-app-site-association."
                )
            )
        }

        return when (val parseResult = parser.parse(response.content)) {
            is AasaParser.ParseResult.Success -> {
                issues.addAll(parseResult.issues)
                val status = when {
                    issues.any { it.severity == AasaIssue.Severity.ERROR && it.code != AasaIssue.AasaIssueCode.MISSING_APPLINKS } ->
                        AasaStatus.INVALID_JSON
                    parseResult.content.applinks == null -> AasaStatus.NO_APPLINKS_SECTION
                    else -> AasaStatus.VALID
                }
                AasaValidation(
                    domain = domain,
                    url = response.finalUrl,
                    status = status,
                    issues = issues,
                    content = parseResult.content,
                    rawJson = response.content,
                    servedFromWellKnown = response.fromWellKnown
                )
            }
            is AasaParser.ParseResult.Error -> {
                issues.addAll(parseResult.issues)
                AasaValidation(
                    domain = domain,
                    url = response.finalUrl,
                    status = AasaStatus.INVALID_JSON,
                    issues = issues,
                    rawJson = response.content,
                    servedFromWellKnown = response.fromWellKnown
                )
            }
        }
    }
}
