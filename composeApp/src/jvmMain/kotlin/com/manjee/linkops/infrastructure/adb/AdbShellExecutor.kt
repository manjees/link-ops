package com.manjee.linkops.infrastructure.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB Shell command executor with streaming support
 *
 * Features:
 * - One-shot command execution with Result<T> error handling
 * - Streaming output via Flow for real-time monitoring (e.g., logcat)
 * - Device-specific command execution
 * - Automatic process lifecycle management
 *
 * @param adbBinaryManager Provides ADB binary path
 */
class AdbShellExecutor(
    private val adbBinaryManager: AdbBinaryManager
) {
    /**
     * Executes ADB command and returns complete output
     *
     * @param command Full ADB command (e.g., "devices -l", "-s serial123 shell getprop")
     * @return Result with stdout content or AdbException
     *
     * Example:
     * ```
     * val result = executor.execute("devices -l")
     * result.onSuccess { output -> println(output) }
     * ```
     */
    suspend fun execute(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val adbPath = adbBinaryManager.getAdbPath()
                ?: throw AdbException("ADB binary not found. Please ensure ADB is installed or downloaded.")

            val commandParts = parseCommand(command)
            val process = ProcessBuilder(listOf(adbPath) + commandParts)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw AdbException(
                    message = "Command failed with exit code $exitCode: $command\nOutput: $output"
                )
            }

            output
        }.recoverCatching { e ->
            if (e is AdbException) {
                throw e
            } else {
                throw AdbException("Failed to execute ADB command: $command", cause = e)
            }
        }
    }

    /**
     * Executes ADB command with streaming output
     *
     * Emits each line as it becomes available. Useful for:
     * - logcat monitoring
     * - Long-running commands
     * - Real-time feedback
     *
     * @param command Full ADB command
     * @return Cold Flow emitting stdout lines
     *
     * Example:
     * ```
     * executor.executeStream("logcat -v time")
     *     .collect { line -> println(line) }
     * ```
     */
    fun executeStream(command: String): Flow<String> = flow {
        val adbPath = adbBinaryManager.getAdbPath()
            ?: throw AdbException("ADB binary not found")

        val commandParts = parseCommand(command)
        val process = ProcessBuilder(listOf(adbPath) + commandParts)
            .redirectErrorStream(true)
            .start()

        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    emit(line!!)
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw AdbException("Stream command failed with exit code $exitCode: $command")
            }
        } catch (e: Exception) {
            process.destroy()
            if (e is AdbException) {
                throw e
            } else {
                throw AdbException("Stream execution failed: $command", e)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Executes shell command on specific device
     *
     * Convenience method that automatically formats device-specific commands.
     *
     * @param serialNumber Device serial number (from `adb devices`)
     * @param shellCommand Shell command to execute (e.g., "getprop ro.build.version.sdk")
     * @return Result with command output
     *
     * Example:
     * ```
     * val result = executor.executeOnDevice("emulator-5554", "pm get-app-links")
     * ```
     */
    suspend fun executeOnDevice(
        serialNumber: String,
        shellCommand: String
    ): Result<String> {
        return execute("-s $serialNumber shell $shellCommand")
    }

    /**
     * Executes streaming command on specific device
     *
     * @param serialNumber Device serial number
     * @param shellCommand Shell command to execute
     * @return Flow emitting stdout lines
     *
     * Example:
     * ```
     * executor.executeStreamOnDevice("emulator-5554", "logcat -v time")
     *     .collect { line -> println(line) }
     * ```
     */
    fun executeStreamOnDevice(
        serialNumber: String,
        shellCommand: String
    ): Flow<String> {
        return executeStream("-s $serialNumber shell $shellCommand")
    }

    /**
     * Parses command string into individual arguments.
     *
     * Honors double-quoted spans so arguments containing spaces stay intact
     * (e.g. `am start ... -d "https://example.com/foo bar"`). Quotes themselves
     * are stripped because ProcessBuilder doesn't go through a shell — passing
     * the quote characters as part of the argument would corrupt the value.
     */
    private fun parseCommand(command: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in command) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}

/**
 * Custom exception for ADB-related errors
 *
 * Thrown when:
 * - ADB binary not found
 * - Command execution fails (non-zero exit code)
 * - Process I/O errors
 * - Invalid command format
 */
class AdbException(message: String, cause: Throwable? = null) : Exception(message, cause)
