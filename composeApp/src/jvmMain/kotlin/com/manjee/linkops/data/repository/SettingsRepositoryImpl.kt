package com.manjee.linkops.data.repository

import com.manjee.linkops.domain.model.AppSettings
import com.manjee.linkops.domain.model.ThemePreference
import com.manjee.linkops.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Serializable on-disk shape. Kept separate from the domain model so that future schema
 * changes (added/removed fields, renames) can be handled by `ignoreUnknownKeys` and
 * mapping logic without forcing a domain model breakage.
 */
@Serializable
private data class AppSettingsDto(
    val theme: String = ThemePreference.SYSTEM.name,
    val adbPathOverride: String? = null,
    val defaultHost: String = AppSettings.DEFAULT_HOST,
    val defaultPort: Int = AppSettings.DEFAULT_PORT,
    val devicePollingIntervalSeconds: Int = AppSettings.DEFAULT_POLLING_INTERVAL_SECONDS
)

/**
 * JSON file-based settings repository.
 *
 * Persists to `~/.linkops/settings.json`. Mirrors the safety model established by
 * FavoriteRepositoryImpl: a Mutex serializes read-modify-write across coroutines and
 * all disk IO runs on `Dispatchers.IO`.
 */
class SettingsRepositoryImpl(
    storageFile: File = resolveStorageFile()
) : SettingsRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val storageFile: File = storageFile
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(loadFromDisk())

    override fun observeSettings(): Flow<AppSettings> = _settings.asStateFlow()

    override val current: AppSettings
        get() = _settings.value

    override suspend fun update(transform: (AppSettings) -> AppSettings): Result<AppSettings> =
        mutex.withLock {
            runCatching {
                val updated = transform(_settings.value).normalized()
                saveToDisk(updated)
                _settings.value = updated
                updated
            }
        }

    private fun loadFromDisk(): AppSettings {
        if (!storageFile.exists()) return AppSettings()
        return try {
            json.decodeFromString<AppSettingsDto>(storageFile.readText()).toDomain()
        } catch (_: Exception) {
            // Corrupt or incompatible file — fall back to defaults rather than crashing.
            // Future write will overwrite the bad file.
            AppSettings()
        }
    }

    private suspend fun saveToDisk(settings: AppSettings) = withContext(Dispatchers.IO) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(json.encodeToString(settings.toDto()))
    }

    private fun AppSettingsDto.toDomain(): AppSettings = AppSettings(
        theme = runCatching { ThemePreference.valueOf(theme) }.getOrDefault(ThemePreference.SYSTEM),
        adbPathOverride = adbPathOverride?.takeIf { it.isNotBlank() },
        defaultHost = defaultHost.ifBlank { AppSettings.DEFAULT_HOST },
        defaultPort = defaultPort,
        devicePollingIntervalSeconds = devicePollingIntervalSeconds
    ).normalized()

    private fun AppSettings.toDto(): AppSettingsDto = AppSettingsDto(
        theme = theme.name,
        adbPathOverride = adbPathOverride,
        defaultHost = defaultHost,
        defaultPort = defaultPort,
        devicePollingIntervalSeconds = devicePollingIntervalSeconds
    )

    /**
     * Clamps numeric fields to their declared safe ranges. Defensive — the UI also
     * validates input, but a corrupted settings file or a future schema migration
     * shouldn't be able to push out-of-range values into the rest of the app.
     */
    private fun AppSettings.normalized(): AppSettings = copy(
        defaultPort = defaultPort.coerceIn(AppSettings.MIN_PORT, AppSettings.MAX_PORT),
        devicePollingIntervalSeconds = devicePollingIntervalSeconds.coerceIn(
            AppSettings.MIN_POLLING_INTERVAL_SECONDS,
            AppSettings.MAX_POLLING_INTERVAL_SECONDS
        ),
        adbPathOverride = adbPathOverride?.takeIf { it.isNotBlank() }
    )

    companion object {
        private const val STORAGE_DIR = ".linkops"
        private const val STORAGE_FILE = "settings.json"

        fun resolveStorageFile(): File {
            val homeDir = System.getProperty("user.home")
            return File(homeDir, "$STORAGE_DIR/$STORAGE_FILE")
        }
    }
}
