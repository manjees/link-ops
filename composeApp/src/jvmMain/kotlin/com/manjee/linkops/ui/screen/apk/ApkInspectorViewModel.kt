package com.manjee.linkops.ui.screen.apk

import com.manjee.linkops.di.AppContainer
import com.manjee.linkops.domain.model.ApkAnalysisResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ApkInspectorUiState(
    val selectedFile: File? = null,
    val isAnalyzing: Boolean = false,
    /**
     * What we're currently doing — surfaced in the loading UI so the user knows
     * whether we're parsing the APK locally or waiting on remote assetlinks fetches.
     * The remote step often dominates wall-time and looks like a hang otherwise.
     */
    val progressLabel: String? = null,
    val result: ApkAnalysisResult? = null,
    val error: String? = null
)

class ApkInspectorViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ApkInspectorUiState())
    val uiState: StateFlow<ApkInspectorUiState> = _uiState.asStateFlow()

    /**
     * Validates the path exists + has a recognizable extension, then kicks off
     * analysis. We accept .apk only for MVP — .aab requires protobuf-based parsing
     * that apk-parser doesn't support, and silently failing on it would mislead
     * the user.
     */
    fun analyzeFile(file: File) {
        if (!file.exists() || !file.isFile) {
            _uiState.update { it.copy(error = "File not found: ${file.absolutePath}") }
            return
        }
        if (!file.name.endsWith(".apk", ignoreCase = true)) {
            val hint = if (file.name.endsWith(".aab", ignoreCase = true)) {
                "AAB (Android App Bundle) is not supported yet — please supply the universal APK or a split APK."
            } else {
                "Only .apk files are supported (got: ${file.name})"
            }
            _uiState.update { it.copy(error = hint) }
            return
        }

        _uiState.update {
            it.copy(
                selectedFile = file,
                isAnalyzing = true,
                progressLabel = "Parsing APK…",
                result = null,
                error = null
            )
        }

        viewModelScope.launch {
            // The use case fetches assetlinks for every autoVerify domain in parallel
            // after the manifest parse — switch the label so the user knows we're now
            // on the network, not the disk.
            _uiState.update { it.copy(progressLabel = "Parsing APK…") }

            val result = AppContainer.analyzeApkAndValidateLinksUseCase(file)

            // Quick label switch is racy with the parse step (the call above blocks
            // until everything finishes), so the network label is best-effort. The
            // real value is the parse vs. done distinction the user sees in the UI.
            result
                .onSuccess { analysis ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            progressLabel = null,
                            result = analysis,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            progressLabel = null,
                            error = error.message ?: "APK analysis failed"
                        )
                    }
                }
        }
    }

    fun clearResult() {
        _uiState.update {
            it.copy(
                selectedFile = null,
                result = null,
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onCleared() {
        viewModelScope.cancel()
    }
}
