package com.manjee.linkops.data.repository

import com.manjee.linkops.domain.model.Favorite
import com.manjee.linkops.domain.repository.FavoriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Serializable DTO for JSON persistence
 */
@Serializable
private data class FavoriteDto(
    val id: String,
    val uri: String,
    val name: String,
    val createdAt: Long
)

/**
 * JSON file-based implementation of FavoriteRepository
 *
 * Persists favorites to ~/.linkops/favorites.json
 */
class FavoriteRepositoryImpl(
    storageFile: File = resolveStorageFile()
) : FavoriteRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val storageFile: File = storageFile
    private val _favorites = MutableStateFlow<List<FavoriteDto>>(emptyList())
    private val mutex = Mutex()

    init {
        _favorites.value = loadFromDisk()
    }

    override fun observeFavorites(): Flow<List<Favorite>> {
        return _favorites.map { dtos -> dtos.map { it.toDomain() }.sortedByDescending { it.createdAt } }
    }

    override suspend fun addFavorite(uri: String, name: String): Result<Favorite> = mutex.withLock {
        runCatching {
            val existing = _favorites.value
            if (existing.any { it.uri == uri }) {
                throw IllegalArgumentException("URI already exists in favorites: $uri")
            }
            val dto = FavoriteDto(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                createdAt = System.currentTimeMillis()
            )
            val updated = existing + dto
            saveToDisk(updated)
            _favorites.value = updated
            dto.toDomain()
        }
    }

    override suspend fun removeFavorite(id: String): Result<Unit> = mutex.withLock {
        runCatching {
            val existing = _favorites.value
            val updated = existing.filter { it.id != id }
            if (updated.size == existing.size) {
                throw NoSuchElementException("Favorite not found: $id")
            }
            saveToDisk(updated)
            _favorites.value = updated
        }
    }

    override suspend fun isFavorite(uri: String): Boolean {
        return _favorites.value.any { it.uri == uri }
    }

    private fun loadFromDisk(): List<FavoriteDto> {
        if (!storageFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<FavoriteDto>>(storageFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveToDisk(favorites: List<FavoriteDto>) = withContext(Dispatchers.IO) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(json.encodeToString(favorites))
    }

    private fun FavoriteDto.toDomain(): Favorite {
        return Favorite(
            id = id,
            uri = uri,
            name = name,
            createdAt = createdAt
        )
    }

    companion object {
        private const val STORAGE_DIR = ".linkops"
        private const val STORAGE_FILE = "favorites.json"

        fun resolveStorageFile(): File {
            val homeDir = System.getProperty("user.home")
            return File(homeDir, "$STORAGE_DIR/$STORAGE_FILE")
        }
    }
}
