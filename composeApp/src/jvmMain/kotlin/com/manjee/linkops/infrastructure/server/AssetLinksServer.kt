package com.manjee.linkops.infrastructure.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.ServerSocket

/**
 * Embedded HTTP server that serves assetlinks.json locally
 *
 * Uses Ktor CIO engine to run a lightweight server on localhost,
 * serving the generated assetlinks.json content at the standard
 * `/.well-known/assetlinks.json` path.
 *
 * Security: Server binds only to the specified host (default: 127.0.0.1)
 * to prevent external access unless explicitly configured.
 */
class AssetLinksServer {

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var currentContent: String = "[]"

    val isRunning: Boolean get() = server != null

    /**
     * Start the server on the specified host and port
     *
     * @param host Host to bind to (default: 127.0.0.1)
     * @param port Port to bind to
     * @param assetLinksJson Content to serve as assetlinks.json
     * @throws ServerException if the server fails to start
     */
    suspend fun start(host: String, port: Int, assetLinksJson: String) {
        if (server != null) {
            stop()
        }

        currentContent = assetLinksJson

        withContext(Dispatchers.IO) {
            try {
                server = embeddedServer(CIO, port = port, host = host) {
                    install(ContentNegotiation) {
                        json(Json { prettyPrint = true })
                    }
                    routing {
                        get("/.well-known/assetlinks.json") {
                            call.respondText(
                                text = currentContent,
                                contentType = ContentType.Application.Json
                            )
                        }
                        get("/health") {
                            call.respondText("OK", ContentType.Text.Plain)
                        }
                    }
                }.also { it.start(wait = false) }
            } catch (e: Exception) {
                server = null
                throw ServerException("Failed to start server on $host:$port", e)
            }
        }
    }

    /**
     * Update the assetlinks.json content while the server is running
     *
     * @param assetLinksJson New content to serve
     */
    fun updateContent(assetLinksJson: String) {
        currentContent = assetLinksJson
    }

    /**
     * Stop the running server
     */
    suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                server?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
            } finally {
                server = null
            }
        }
    }

    companion object {
        /**
         * Check if a port is available on the given host
         *
         * @param host Host to check
         * @param port Port to check
         * @return true if the port is available
         */
        fun isPortAvailable(host: String, port: Int): Boolean {
            return try {
                ServerSocket(port, 1, java.net.InetAddress.getByName(host)).use { true }
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Exception for server-related errors
 */
class ServerException(message: String, cause: Throwable? = null) : Exception(message, cause)
