package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.BatchTestResult
import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import com.manjee.linkops.domain.repository.BatchTestRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * UseCase for executing a batch deep link test scenario
 */
class ExecuteBatchTestUseCase(
    private val batchTestRepository: BatchTestRepository
) {
    /**
     * Executes a test scenario by launching all resolved URIs sequentially
     *
     * @param deviceSerial Device serial number
     * @param scenario Test scenario to execute
     * @param resolvedUris Pre-resolved URIs (with variables substituted)
     * @return Flow emitting each LaunchResult as it completes
     */
    operator fun invoke(
        deviceSerial: String,
        scenario: TestScenario,
        resolvedUris: List<String>
    ): Flow<LaunchResult> = flow {
        for (uri in resolvedUris) {
            val result = batchTestRepository.launchDeepLink(deviceSerial, uri)
            emit(
                result.getOrElse { error ->
                    LaunchResult.Error(
                        uri = uri,
                        errorMessage = error.message ?: "Unknown error",
                        timeTakenMs = 0
                    )
                }
            )
            delay(scenario.delayBetweenLaunchesMs)
        }
    }
}
