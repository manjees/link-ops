package com.manjee.linkops.data.mapper

import com.manjee.linkops.domain.model.DeepLinkTemplate
import com.manjee.linkops.domain.model.TestScenario
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps between domain TestScenario and serializable DTO for JSON export/import
 */
class ScenarioMapper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serializes a TestScenario to JSON string
     *
     * @param scenario Domain model
     * @return JSON string representation
     */
    fun toJson(scenario: TestScenario): String {
        val dto = TestScenarioDto(
            id = scenario.id,
            name = scenario.name,
            description = scenario.description,
            templates = scenario.templates.map { template ->
                DeepLinkTemplateDto(
                    uri = template.uri,
                    name = template.name,
                    expectedActivity = template.expectedActivity
                )
            },
            variables = scenario.variables,
            delayBetweenLaunchesMs = scenario.delayBetweenLaunchesMs
        )
        return json.encodeToString(dto)
    }

    /**
     * Deserializes a JSON string to TestScenario
     *
     * @param jsonString JSON string
     * @return Domain model
     */
    fun fromJson(jsonString: String): TestScenario {
        val dto = json.decodeFromString<TestScenarioDto>(jsonString)
        return TestScenario(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            templates = dto.templates.map { templateDto ->
                DeepLinkTemplate(
                    uri = templateDto.uri,
                    name = templateDto.name,
                    expectedActivity = templateDto.expectedActivity
                )
            },
            variables = dto.variables,
            delayBetweenLaunchesMs = dto.delayBetweenLaunchesMs
        )
    }
}

/**
 * Serializable DTO for TestScenario JSON export/import
 */
@Serializable
private data class TestScenarioDto(
    val id: String,
    val name: String,
    val description: String = "",
    val templates: List<DeepLinkTemplateDto> = emptyList(),
    val variables: Map<String, List<String>> = emptyMap(),
    val delayBetweenLaunchesMs: Long = TestScenario.DEFAULT_DELAY_MS
)

/**
 * Serializable DTO for DeepLinkTemplate
 */
@Serializable
private data class DeepLinkTemplateDto(
    val uri: String,
    val name: String = "",
    val expectedActivity: String = ""
)
