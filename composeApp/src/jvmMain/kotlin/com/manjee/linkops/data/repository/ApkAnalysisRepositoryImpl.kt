package com.manjee.linkops.data.repository

import com.manjee.linkops.domain.model.ApkAnalysisResult
import com.manjee.linkops.domain.repository.ApkAnalysisRepository
import com.manjee.linkops.infrastructure.apk.ApkAnalyzer
import java.io.File

class ApkAnalysisRepositoryImpl(
    private val analyzer: ApkAnalyzer
) : ApkAnalysisRepository {

    override suspend fun analyzeApk(apkFile: File): Result<ApkAnalysisResult> {
        return analyzer.analyze(apkFile).map { analyzed ->
            ApkAnalysisResult(
                info = analyzed.info,
                signatures = analyzed.signatures,
                intentFilters = analyzed.intentFilters,
                linkDomains = analyzed.linkDomains,
                domainVerifications = emptyList()
            )
        }
    }
}
