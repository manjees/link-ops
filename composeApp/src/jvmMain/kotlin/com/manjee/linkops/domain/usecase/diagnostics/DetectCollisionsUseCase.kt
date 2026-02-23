package com.manjee.linkops.domain.usecase.diagnostics

import com.manjee.linkops.domain.model.CollisionReport
import com.manjee.linkops.domain.repository.CollisionRepository

/**
 * Detects URI scheme collisions across installed apps on a device
 */
class DetectCollisionsUseCase(
    private val collisionRepository: CollisionRepository
) {
    /**
     * Scan installed apps and detect URI scheme collisions
     *
     * @param deviceSerial Serial number of the target device
     * @return CollisionReport with detected collision groups
     */
    suspend operator fun invoke(deviceSerial: String): Result<CollisionReport> {
        return collisionRepository.detectCollisions(deviceSerial)
    }
}
