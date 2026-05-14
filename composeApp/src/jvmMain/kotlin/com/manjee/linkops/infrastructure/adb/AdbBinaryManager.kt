package com.manjee.linkops.infrastructure.adb

import com.manjee.linkops.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * ADB binary auto-download and management
 * - Uses user-configured override if set
 * - Falls back to system ADB if installed
 * - Falls back to bundled / downloaded binary
 *
 * The settings repository is optional so that tests and headless tooling can construct
 * this without wiring the full DI graph.
 */
class AdbBinaryManager(
    private val settingsRepository: SettingsRepository? = null
) {
    private val os: String = System.getProperty("os.name").lowercase()
    private val adbDir = File(System.getProperty("user.home"), ".linkops/adb")

    /**
     * Returns ADB binary path
     * 1. User override from settings (if configured and the file is executable)
     * 2. ADB on system PATH
     * 3. Bundled / previously downloaded binary
     * 4. null when a download is required
     */
    fun getAdbPath(): String? {
        // 1. Honor explicit user override when it points to an executable file.
        // Falling through silently on a broken override would hide a misconfiguration —
        // but treating it as fatal would lock the user out of ADB entirely if they typo.
        // The Settings UI surfaces the value so they can fix it.
        val override = settingsRepository?.current?.adbPathOverride
        if (!override.isNullOrBlank()) {
            val file = File(override)
            if (file.exists() && file.canExecute()) return file.absolutePath
        }

        // 2. Search for ADB in system PATH
        val systemAdb = findSystemAdb()
        if (systemAdb != null) return systemAdb

        // 3. Check bundled ADB directory
        val bundledAdb = getBundledAdbFile()
        if (bundledAdb.exists() && bundledAdb.canExecute()) {
            return bundledAdb.absolutePath
        }

        return null
    }

    /**
     * Check if ADB is available
     */
    fun isAdbAvailable(): Boolean = getAdbPath() != null

    /**
     * Check if ADB download is required
     */
    fun needsDownload(): Boolean = getAdbPath() == null

    /**
     * Download and install ADB
     * @param onProgress Download progress callback (0.0 ~ 1.0)
     * @return Result with ADB path on success
     */
    suspend fun downloadAdb(onProgress: (Float) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            adbDir.mkdirs()

            val downloadUrl = getDownloadUrl()
            val zipFile = File(adbDir, "platform-tools.zip")

            // Download ZIP
            downloadFile(downloadUrl, zipFile, onProgress)

            // Extract ZIP
            extractZip(zipFile, adbDir)

            // Delete ZIP file
            zipFile.delete()

            // Grant execute permission (Unix-based systems)
            val adbFile = getBundledAdbFile()
            if (!os.contains("win")) {
                adbFile.setExecutable(true)
            }

            adbFile.absolutePath
        }
    }

    /**
     * Find ADB in system PATH
     */
    private fun findSystemAdb(): String? {
        return try {
            val command = if (os.contains("win")) {
                listOf("where", "adb")
            } else {
                listOf("which", "adb")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val path = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && path.isNotEmpty()) {
                // Use first path if multiple lines exist
                path.lines().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get bundled ADB file path
     */
    private fun getBundledAdbFile(): File {
        val platformToolsDir = File(adbDir, "platform-tools")
        return File(platformToolsDir, adbBinaryName)
    }

    /**
     * Get download URL for current OS
     */
    private fun getDownloadUrl(): String {
        return when {
            os.contains("mac") -> ADB_URL_MAC
            os.contains("win") -> ADB_URL_WINDOWS
            os.contains("linux") || os.contains("nux") -> ADB_URL_LINUX
            else -> throw UnsupportedOperationException("Unsupported OS: $os")
        }
    }

    /**
     * Download file from URL
     */
    private fun downloadFile(urlString: String, destination: File, onProgress: (Float) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000

        try {
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: ${connection.responseCode}")
            }

            val contentLength = connection.contentLength.toLong()
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (contentLength > 0) {
                            onProgress((downloaded.toFloat() / contentLength))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extract ZIP file
     */
    private fun extractZip(zipFile: File, destination: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                val file = File(destination, entry.name)

                // Prevent directory traversal attack
                if (!file.canonicalPath.startsWith(destination.canonicalPath)) {
                    throw SecurityException("ZIP entry is outside of the target directory")
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * ADB binary name for current OS
     */
    private val adbBinaryName: String
        get() = if (os.contains("win")) "adb.exe" else "adb"

    /**
     * Current OS type
     */
    val currentOs: OsType
        get() = when {
            os.contains("mac") -> OsType.MAC
            os.contains("win") -> OsType.WINDOWS
            os.contains("linux") || os.contains("nux") -> OsType.LINUX
            else -> OsType.UNKNOWN
        }

    enum class OsType {
        MAC, WINDOWS, LINUX, UNKNOWN
    }

    companion object {
        private const val ADB_URL_MAC =
            "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
        private const val ADB_URL_WINDOWS =
            "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
        private const val ADB_URL_LINUX =
            "https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
    }
}
