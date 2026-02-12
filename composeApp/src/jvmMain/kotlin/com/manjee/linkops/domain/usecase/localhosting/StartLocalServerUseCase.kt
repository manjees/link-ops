package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.model.LocalHostingConfig
import com.manjee.linkops.domain.repository.LocalHostingRepository

/**
 * UseCase for starting the local AssetLinks hosting server
 */
class StartLocalServerUseCase(
    private val localHostingRepository: LocalHostingRepository
) {
    /**
     * Starts the local server with the given configuration
     * @param config Server configuration
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(config: LocalHostingConfig): Result<Unit> {
        return localHostingRepository.startServer(config)
    }
}
