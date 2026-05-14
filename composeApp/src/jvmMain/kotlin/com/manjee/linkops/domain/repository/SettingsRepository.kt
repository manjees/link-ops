package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /**
     * Observes the current settings. Emits immediately with the loaded value and then
     * again on every persisted change.
     */
    fun observeSettings(): Flow<AppSettings>

    /**
     * Synchronous snapshot of the current settings. Used by callers that can't suspend
     * (e.g. [com.manjee.linkops.infrastructure.adb.AdbBinaryManager.getAdbPath] resolving
     * an override before issuing an ADB command).
     */
    val current: AppSettings

    /**
     * Applies a transform to the current settings and persists the result.
     * The transform must be pure (no side effects); the repository serializes concurrent
     * updates so the read-modify-write is atomic.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings): Result<AppSettings>
}
