package com.manjee.linkops.data.repository

import com.manjee.linkops.data.mapper.ScenarioMapper
import com.manjee.linkops.data.parser.AmStartOutputParser
import com.manjee.linkops.data.parser.ParsedAmStartResult
import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import com.manjee.linkops.domain.repository.BatchTestRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor

/**
 * Implementation of BatchTestRepository using ADB commands
 */
class BatchTestRepositoryImpl(
    private val adbExecutor: AdbShellExecutor,
    private val amStartOutputParser: AmStartOutputParser,
    private val scenarioMapper: ScenarioMapper
) : BatchTestRepository {

    override suspend fun launchDeepLink(
        deviceSerial: String,
        uri: String
    ): Result<LaunchResult> {
        val startTime = System.currentTimeMillis()
        val command = buildAmStartCommand(uri)

        return adbExecutor.executeOnDevice(deviceSerial, command)
            .mapCatching { output ->
                val elapsed = System.currentTimeMillis() - startTime
                when (val parsed = amStartOutputParser.parse(output)) {
                    is ParsedAmStartResult.Success -> LaunchResult.Success(
                        uri = uri,
                        resolvedActivity = parsed.resolvedActivity,
                        timeTakenMs = elapsed
                    )
                    is ParsedAmStartResult.Error -> LaunchResult.Error(
                        uri = uri,
                        errorMessage = parsed.message,
                        timeTakenMs = elapsed
                    )
                }
            }
            .recoverCatching { error ->
                val elapsed = System.currentTimeMillis() - startTime
                LaunchResult.Error(
                    uri = uri,
                    errorMessage = error.message ?: "Unknown ADB error",
                    timeTakenMs = elapsed
                )
            }
    }

    override suspend fun exportScenario(scenario: TestScenario): Result<String> {
        return runCatching {
            scenarioMapper.toJson(scenario)
        }
    }

    override suspend fun importScenario(json: String): Result<TestScenario> {
        return runCatching {
            scenarioMapper.fromJson(json)
        }
    }

    private fun buildAmStartCommand(uri: String): String {
        return "am start -W -a android.intent.action.VIEW -d \"$uri\""
    }
}
