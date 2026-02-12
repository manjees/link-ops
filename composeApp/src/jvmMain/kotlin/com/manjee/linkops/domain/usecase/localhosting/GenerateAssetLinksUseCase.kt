package com.manjee.linkops.domain.usecase.localhosting

import com.manjee.linkops.domain.repository.LocalHostingRepository

/**
 * UseCase for generating assetlinks.json content
 */
class GenerateAssetLinksUseCase(
    private val localHostingRepository: LocalHostingRepository
) {
    /**
     * Generates assetlinks.json content for the given package and fingerprints
     * @param packageName Android application package name
     * @param sha256Fingerprints SHA-256 certificate fingerprints
     * @return Result with the generated JSON string
     */
    suspend operator fun invoke(
        packageName: String,
        sha256Fingerprints: List<String>
    ): Result<String> {
        return localHostingRepository.generateAssetLinksJson(packageName, sha256Fingerprints)
    }
}
