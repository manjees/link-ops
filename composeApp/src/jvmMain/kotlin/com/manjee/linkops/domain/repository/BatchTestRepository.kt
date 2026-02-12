package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.LaunchResult
import com.manjee.linkops.domain.model.TestScenario
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for batch deep link testing operations
 */
interface BatchTestRepository {
    /**
     * Launches a single deep link intent on the device and captures the result
     *
     * @param deviceSerial Device serial number
     * @param uri Fully resolved deep link URI
     * @return Result with launch outcome
     */
    suspend fun launchDeepLink(deviceSerial: String, uri: String): Result<LaunchResult>

    /**
     * Exports a test scenario to JSON string
     *
     * @param scenario Test scenario to serialize
     * @return Result with JSON string
     */
    suspend fun exportScenario(scenario: TestScenario): Result<String>

    /**
     * Imports a test scenario from JSON string
     *
     * @param json JSON string to deserialize
     * @return Result with parsed test scenario
     */
    suspend fun importScenario(json: String): Result<TestScenario>
}
