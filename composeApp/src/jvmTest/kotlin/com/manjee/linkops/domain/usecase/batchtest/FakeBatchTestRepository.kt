package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import com.manjee.linkops.domain.repository.BatchTestRepository

/**
 * Fake implementation of BatchTestRepository for testing
 */
class FakeBatchTestRepository : BatchTestRepository {

    var launchResults = mutableMapOf<String, Result<LaunchResult>>()
    var exportResult: Result<String> = Result.success("{}")
    var importResult: Result<TestScenario> = Result.success(
        TestScenario(id = "test", name = "Test")
    )

    override suspend fun launchDeepLink(
        deviceSerial: String,
        uri: String
    ): Result<LaunchResult> {
        return launchResults[uri] ?: Result.success(
            LaunchResult.Success(
                uri = uri,
                resolvedActivity = "com.test/.TestActivity",
                timeTakenMs = 100
            )
        )
    }

    override suspend fun exportScenario(scenario: TestScenario): Result<String> {
        return exportResult
    }

    override suspend fun importScenario(json: String): Result<TestScenario> {
        return importResult
    }
}
