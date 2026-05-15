package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.ApkAnalysisResult
import java.io.File

interface ApkAnalysisRepository {
    /**
     * Parses [apkFile] in-process — no installation, no ADB. The result includes
     * every signing certificate, every intent-filter, and a deduplicated list of
     * link domains; it does NOT include assetlinks cross-checking (see the use case
     * that pairs this with `ValidateAssetLinksUseCase`).
     */
    suspend fun analyzeApk(apkFile: File): Result<ApkAnalysisResult>
}
