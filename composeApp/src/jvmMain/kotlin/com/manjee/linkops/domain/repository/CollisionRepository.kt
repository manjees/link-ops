package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.CollisionReport

/**
 * Repository for detecting URI scheme collisions across installed apps
 */
interface CollisionRepository {
    /**
     * Scan installed apps on a device and detect URI scheme collisions
     *
     * @param deviceSerial Serial number of the target device
     * @return CollisionReport with detected collision groups
     */
    suspend fun detectCollisions(deviceSerial: String): Result<CollisionReport>
}
