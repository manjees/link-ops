package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.repository.LocalHostingRepository

/**
 * UseCase for stopping the local AssetLinks hosting server
 */
class StopLocalServerUseCase(
    private val localHostingRepository: LocalHostingRepository
) {
    /**
     * Stops the currently running local server
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(): Result<Unit> {
        return localHostingRepository.stopServer()
    }
}
