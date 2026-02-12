package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.model.VerificationStep
import com.manjee.linkops.domain.repository.LocalHostingRepository
import kotlinx.coroutines.flow.Flow

/**
 * UseCase for running the full pre-deployment verification workflow
 */
class RunVerificationWorkflowUseCase(
    private val localHostingRepository: LocalHostingRepository
) {
    /**
     * Runs the full verification workflow:
     * start server -> trigger pm verify-app-links -> observe results
     * @param deviceSerial Device serial number
     * @param packageName Package name to verify
     * @param config Server configuration
     * @return Flow of verification steps with progress
     */
    operator fun invoke(
        deviceSerial: String,
        packageName: String,
        config: LocalHostingConfig
    ): Flow<VerificationStep> {
        return localHostingRepository.runVerificationWorkflow(deviceSerial, packageName, config)
    }
}
