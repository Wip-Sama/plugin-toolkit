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
}

@Serializable
enum class PrimitiveType {
    DOUBLE, INT, STRING, BOOLEAN, UNIT, ANY
}

@Serializable
data class PluginManifest(
    val manifestVersion: String,
    val module: ModuleInfo,
    val requirements: Requirements,
    val defaultParameters: Map<String, ParameterMetadata>? = null,
    val capabilities: List<Capability> = emptyList()
)

@Serializable
data class ModuleInfo(
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
    val value: JsonElement? = null,
    val description: String,
    val type: DataType
)

@Serializable
data class Capability(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterMetadata>? = null,
    val returnType: DataType
)
