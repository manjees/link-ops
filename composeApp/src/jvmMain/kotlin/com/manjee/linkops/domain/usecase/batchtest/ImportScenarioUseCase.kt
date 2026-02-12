package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.TestScenario
import com.manjee.linkops.domain.repository.BatchTestRepository

/**
 * UseCase for importing a test scenario from JSON
 */
class ImportScenarioUseCase(
    private val batchTestRepository: BatchTestRepository
) {
    /**
     * Imports a test scenario from JSON string
     *
     * @param json JSON string to parse
     * @return Result with parsed test scenario
     */
    suspend operator fun invoke(json: String): Result<TestScenario> {
        return batchTestRepository.importScenario(json)
    }
}
