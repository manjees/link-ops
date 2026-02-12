package com.manjee.linkops.domain.model

/**
 * Configuration for the local AssetLinks hosting server
 */
data class LocalHostingConfig(
    val host: String = "127.0.0.1",
    val port: Int = DEFAULT_PORT,
    val packageName: String = "",
    val sha256Fingerprints: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_PORT = 8080
    }

    val serverUrl: String get() = "http://$host:$port"
    val assetLinksUrl: String get() = "$serverUrl/.well-known/assetlinks.json"
    val isValid: Boolean get() = packageName.isNotBlank() && sha256Fingerprints.isNotEmpty()
}

/**
 * Represents the current state of the local hosting server
 */
enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

/**
 * A single step in the verification workflow
 */
data class VerificationStep(
    val name: String,
    val status: StepStatus,
    val message: String = "",
    val timestamp: Long = 0L
) {
    enum class StepStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILURE
    }
}
