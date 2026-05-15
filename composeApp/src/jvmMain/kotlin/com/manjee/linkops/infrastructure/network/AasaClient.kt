package com.manjee.linkops.infrastructure.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * HTTP client for fetching `apple-app-site-association` (AASA) files.
 *
 * Apple supports two locations:
 * 1. `https://<domain>/.well-known/apple-app-site-association` (iOS 13+ recommended)
 * 2. `https://<domain>/apple-app-site-association` (legacy)
 *
 * We try the well-known path first. Only if it returns 404 do we fall back to
 * the root path — any other failure on the well-known path is reported as-is
 * because falling back would mask real problems (e.g. an SSL error at the
 * domain, not a missing file).
 */
class AasaClient {
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
        followRedirects = false
    }

    suspend fun fetch(domain: String): AasaResponse {
        val wellKnownResult = fetchOne(buildUrl(domain, wellKnown = true), wellKnown = true)
        if (wellKnownResult !is AasaResponse.NotFound) return wellKnownResult

        // Only fall back when the well-known location is genuinely 404 — any
        // other status is a real signal we want to preserve.
        return fetchOne(buildUrl(domain, wellKnown = false), wellKnown = false)
    }

    private suspend fun fetchOne(url: String, wellKnown: Boolean): AasaResponse {
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "LinkOps/1.0")
            }

            when (response.status) {
                HttpStatusCode.OK -> AasaResponse.Success(
                    content = response.bodyAsText(),
                    contentType = response.contentType()?.toString() ?: "",
                    finalUrl = url,
                    fromWellKnown = wellKnown
                )
                HttpStatusCode.NotFound -> AasaResponse.NotFound(url)
                HttpStatusCode.MovedPermanently,
                HttpStatusCode.Found,
                HttpStatusCode.TemporaryRedirect,
                HttpStatusCode.PermanentRedirect -> AasaResponse.Redirect(
                    originalUrl = url,
                    redirectUrl = response.headers[HttpHeaders.Location]
                )
                else -> AasaResponse.HttpError(
                    statusCode = response.status.value,
                    message = response.status.description,
                    url = url
                )
            }
        } catch (e: HttpRequestTimeoutException) {
            AasaResponse.NetworkError(
                message = "Request timed out",
                url = url,
                cause = e
            )
        } catch (e: Exception) {
            AasaResponse.NetworkError(
                message = e.message ?: "Unknown network error",
                url = url,
                cause = e
            )
        }
    }

    private fun buildUrl(domain: String, wellKnown: Boolean): String {
        return if (wellKnown) {
            "https://$domain/.well-known/apple-app-site-association"
        } else {
            "https://$domain/apple-app-site-association"
        }
    }

    fun close() {
        httpClient.close()
    }
}

sealed class AasaResponse {
    data class Success(
        val content: String,
        val contentType: String,
        val finalUrl: String,
        val fromWellKnown: Boolean
    ) : AasaResponse()

    data class NotFound(val url: String) : AasaResponse()

    data class Redirect(
        val originalUrl: String,
        val redirectUrl: String?
    ) : AasaResponse()

    data class HttpError(
        val statusCode: Int,
        val message: String,
        val url: String
    ) : AasaResponse()

    data class NetworkError(
        val message: String,
        val url: String,
        val cause: Throwable? = null
    ) : AasaResponse()
}
