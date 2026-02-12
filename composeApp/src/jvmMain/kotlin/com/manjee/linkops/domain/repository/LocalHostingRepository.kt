package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.model.ServerStatus
import com.manjee.linkops.domain.model.VerificationStep
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for local AssetLinks server hosting operations
 */
interface LocalHostingRepository {
    /**
     * Observes the current server status
     * @return Flow of server status updates
     */
    fun observeServerStatus(): Flow<ServerStatus>

    /**
     * Starts the local AssetLinks server with the given configuration
     * @param config Server configuration including port, package name, and fingerprints
     * @return Result indicating success or failure
     */
    suspend fun startServer(config: LocalHostingConfig): Result<Unit>

    /**
     * Stops the currently running local server
     * @return Result indicating success or failure
     */
    suspend fun stopServer(): Result<Unit>

    /**
     * Generates the assetlinks.json content for the given configuration
     * @param packageName Android package name
     * @param sha256Fingerprints SHA-256 certificate fingerprints
     * @return Result with the generated JSON string
     */
    suspend fun generateAssetLinksJson(
        packageName: String,
        sha256Fingerprints: List<String>
    ): Result<String>

    /**
     * Extracts the signing certificate fingerprint from a connected device
     * @param deviceSerial Device serial number
     * @param packageName Package to extract fingerprint from
     * @return Result with the SHA-256 fingerprint string
     */
    suspend fun extractFingerprint(
        deviceSerial: String,
        packageName: String
    ): Result<String>

    /**
     * Runs the full verification workflow:
     * start server -> trigger pm verify-app-links -> observe results
     * @param deviceSerial Device serial number
     * @param packageName Package name to verify
     * @param config Server configuration
     * @return Flow of verification steps with progress
     */
    fun runVerificationWorkflow(
        deviceSerial: String,
        packageName: String,
        config: LocalHostingConfig
    ): Flow<VerificationStep>
}
