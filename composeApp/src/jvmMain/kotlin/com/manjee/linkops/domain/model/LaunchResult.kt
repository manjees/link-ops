package com.manjee.linkops.domain.model

/**
 * Result of a single deep link launch via ADB
 */
sealed class LaunchResult {
    /**
     * The URI that was launched or attempted
     */
    abstract val uri: String

    /**
     * Deep link was successfully launched
     *
     * @param uri The URI that was launched
     * @param resolvedActivity The Activity class that handled the intent
     * @param timeTakenMs Time taken for the launch in milliseconds
     */
    data class Success(
        override val uri: String,
        val resolvedActivity: String,
        val timeTakenMs: Long
    ) : LaunchResult()

    /**
     * Deep link launch failed with an error
     *
     * @param uri The URI that was attempted
     * @param errorMessage Description of the error
     * @param timeTakenMs Time taken before failure in milliseconds
     */
    data class Error(
        override val uri: String,
        val errorMessage: String,
        val timeTakenMs: Long
    ) : LaunchResult()

    val isSuccess: Boolean get() = this is Success
}

/**
 * Aggregated result of a batch test execution
 *
 * @param scenarioName Name of the executed test scenario
 * @param results Individual launch results
 * @param totalTimeMs Total execution time in milliseconds
 */
data class BatchTestResult(
    val scenarioName: String,
    val results: List<LaunchResult>,
    val totalTimeMs: Long
) {
    val successCount: Int get() = results.count { it.isSuccess }
    val failureCount: Int get() = results.size - successCount
    val totalCount: Int get() = results.size
    val successRate: Float
        get() = if (totalCount > 0) successCount.toFloat() / totalCount else 0f
}
