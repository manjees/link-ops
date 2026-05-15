package com.manjee.linkops.domain.usecase.apk

import com.manjee.linkops.domain.model.ApkAnalysisResult
import com.manjee.linkops.domain.repository.ApkAnalysisRepository
import java.io.File

class AnalyzeApkUseCase(
    private val apkAnalysisRepository: ApkAnalysisRepository
) {
    suspend operator fun invoke(apkFile: File): Result<ApkAnalysisResult> {
        return apkAnalysisRepository.analyzeApk(apkFile)
    }
}
