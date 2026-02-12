package com.manjee.linkops.data.generator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for serializing assetlinks.json statements
 */
@Serializable
data class AssetLinksStatementDto(
    val relation: List<String>,
    val target: AssetLinksTargetDto
)

/**
 * DTO for serializing assetlinks.json target
 */
@Serializable
data class AssetLinksTargetDto(
    val namespace: String,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("sha256_cert_fingerprints")
    val sha256CertFingerprints: List<String>
)
