package com.wip.plugin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class DataType {
    @Serializable
    @SerialName("primitive")
    data class Primitive(val primitiveType: PrimitiveType) : DataType()

    @Serializable
    @SerialName("array")
    data class Array(val items: DataType) : DataType()

    @Serializable
    @SerialName("object")
    data class Object(val className: String) : DataType()

    @Serializable
    @SerialName("enum")
    data class Enum(val className: String, val options: List<String>) : DataType()
}

@Serializable
enum class PrimitiveType {
    DOUBLE, INT, STRING, BOOLEAN, UNIT, ANY
}

@Serializable
enum class UpdateType {
    REPLACE_ALL, SCRIPT, PATCH
}

@Serializable
data class ParameterConstraints(
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val regex: String? = null,
    val multiSelect: Boolean? = null,
    val minChoices: Int? = null,
    val maxChoices: Int? = null
)

@Serializable
data class SettingMetadata(
    val defaultValue: JsonElement? = null,
    val description: String,
    val type: DataType
)

@Serializable
data class PluginManifest(
    val manifestVersion: String,
    val plugin: PluginInfo,
    val requirements: Requirements,
    val defaultParameters: Map<String, ParameterMetadata>? = null,
    val capabilities: List<Capability> = emptyList(),
    val settings: Map<String, SettingMetadata>? = null,
    val changelog: Changelog? = null,
    val updateType: UpdateType = UpdateType.REPLACE_ALL
)

@Serializable
data class Changelog(
    val releases: List<Release>
)

@Serializable
data class Release(
    val version: String,
    val date: String,
    val categories: Map<String, List<String>>
)

@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String
)

@Serializable
data class Requirements(
    val minMemoryMb: Int,
    val minExecutionTimeMs: Int
)

@Serializable
data class ParameterMetadata(
    val defaultValue: JsonElement? = null,
    val description: String,
    val type: DataType,
    val constraints: ParameterConstraints? = null
)

@Serializable
data class Capability(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterMetadata>? = null,
    val returnType: DataType,
    val isPausable: Boolean = false,
    val isCancellable: Boolean = true
)
