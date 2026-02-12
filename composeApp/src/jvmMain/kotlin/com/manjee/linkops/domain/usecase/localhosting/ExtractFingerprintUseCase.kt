package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.repository.LocalHostingRepository

/**
 * UseCase for extracting the signing certificate fingerprint from a device
 */
class ExtractFingerprintUseCase(
    private val localHostingRepository: LocalHostingRepository
) {
    /**
     * Extracts the SHA-256 signing certificate fingerprint for a package on a device
     * @param deviceSerial Device serial number
     * @param packageName Package name to extract fingerprint from
     * @return Result with the SHA-256 fingerprint string
     */
    suspend operator fun invoke(
        deviceSerial: String,
        packageName: String
    ): Result<String> {
        return localHostingRepository.extractFingerprint(deviceSerial, packageName)
    }
}
