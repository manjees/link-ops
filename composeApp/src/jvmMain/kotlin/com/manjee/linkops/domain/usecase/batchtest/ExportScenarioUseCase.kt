package com.manjee.linkops.domain.usecase.batchtest

import com.manjee.linkops.domain.model.TestScenario
import com.manjee.linkops.domain.repository.BatchTestRepository

/**
 * UseCase for exporting a test scenario to JSON
 */
class ExportScenarioUseCase(
    private val batchTestRepository: BatchTestRepository
) {
    /**
     * Exports a test scenario to JSON string
     *
     * @param scenario Test scenario to export
     * @return Result with JSON string
     */
    suspend operator fun invoke(scenario: TestScenario): Result<String> {
        return batchTestRepository.exportScenario(scenario)
    }
}
