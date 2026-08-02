package org.wip.plugintoolkit.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

inline fun <reified T : Enum<T>> createSafeEnumSerializer(
    serialName: String,
    fallback: T
): KSerializer<T> = object : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): T {
        val name = decoder.decodeString()
        return enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: fallback
    }
}

object DataTypePrimitiveFromStringSerializer : KSerializer<DataType.Primitive> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DataType.PrimitiveFromString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DataType.Primitive) {
        encoder.encodeString(value.primitiveType.name)
    }

    override fun deserialize(decoder: Decoder): DataType.Primitive {
        val str = decoder.decodeString()
        val prim = enumValues<PrimitiveType>().firstOrNull { it.name.equals(str, ignoreCase = true) } ?: PrimitiveType.UNKNOWN
        return DataType.Primitive(prim)
    }
}

object DataTypeSerializer : JsonContentPolymorphicSerializer<DataType>(DataType::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out DataType> {
        if (element is JsonPrimitive) {
            return DataTypePrimitiveFromStringSerializer
        }
        val jsonObj = element as? JsonObject ?: return DataType.Unknown.serializer()
        val type = jsonObj["type"]?.jsonPrimitive?.contentOrNull?.lowercase()

        if (type != null) {
            when {
                type == "primitive" || type.endsWith(".primitive") -> return DataType.Primitive.serializer()
                type == "array" || type.endsWith(".array") -> return DataType.Array.serializer()
                type == "object" || type.endsWith(".object") -> return DataType.Object.serializer()
                type == "enum" || type.endsWith(".enum") -> return DataType.Enum.serializer()
                type == "map" || type == "maptype" || type.endsWith(".maptype") || type.endsWith(".map") -> return DataType.MapType.serializer()
                type == "unknown" || type.endsWith(".unknown") -> return DataType.Unknown.serializer()
            }
        }

        // Structural fallbacks when "type" discriminator is missing or unrecognized
        return when {
            "primitiveType" in jsonObj -> DataType.Primitive.serializer()
            "items" in jsonObj -> DataType.Array.serializer()
            "options" in jsonObj -> DataType.Enum.serializer()
            "valueType" in jsonObj -> DataType.MapType.serializer()
            "className" in jsonObj || "properties" in jsonObj -> DataType.Object.serializer()
            "rawType" in jsonObj -> DataType.Unknown.serializer()
            else -> DataType.Unknown.serializer()
        }
    }
}

object DataTypeUnknownSerializer : KSerializer<DataType.Unknown> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DataType.Unknown")

    override fun serialize(encoder: Encoder, value: DataType.Unknown) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.rawType)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): DataType.Unknown {
        val input = decoder as? JsonDecoder ?: return DataType.Unknown("unknown")
        val element = input.decodeJsonElement()
        val rawType = (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull ?: "unknown"
        return DataType.Unknown(rawType)
    }
}

/**
 * Represents the type of data exchanged between the host and the plugin.
 */
@Serializable(with = DataTypeSerializer::class)
sealed class DataType {
    /**
     * Checks if a value is "provided" for this data type.
     * This is used to validate required settings or parameters.
     * For example, a String is provided if it's not blank.
     * An Array is provided if it's not empty.
     */
    abstract fun isProvided(value: JsonElement?): Boolean

    /**
     * A basic primitive type (String, Int, Boolean, etc.).
     */
    @Serializable
    @SerialName("primitive")
    data class Primitive(val primitiveType: PrimitiveType) : DataType() {
        override fun isProvided(value: JsonElement?): Boolean {
            if (value == null || value is JsonNull) return false
            return when (primitiveType) {
                PrimitiveType.STRING -> (value as? JsonPrimitive)?.content?.isNotBlank() ?: false
                else -> true // Other primitives are provided if they exist
            }
        }
    }

    /**
     * A collection of items of the same type.
     */
    @Serializable
    @SerialName("array")
    data class Array(val items: DataType) : DataType() {
        override fun isProvided(value: JsonElement?): Boolean {
            if (value == null || value is JsonNull) return false
            val array = value as? JsonArray ?: return false
            if (array.isEmpty()) return false
            return array.all { items.isProvided(it) }
        }
    }

    /**
     * A custom object type represented by its class name.
     */
    @Serializable
    @SerialName("object")
    data class Object(
        val className: String,
        val id: String? = null,
        val description: String? = null,
        val version: Int? = null,
        val namespace: String? = null,
        val properties: Map<String, DataType> = emptyMap(),
        val requiredProperties: List<String> = emptyList()
    ) : DataType() {
        override fun isProvided(value: JsonElement?): Boolean {
            if (value == null || value is JsonNull) return false
            val jsonObj = value as? JsonObject ?: return false
            if (requiredProperties.isEmpty()) return true

            return requiredProperties.all { prop ->
                val propType = properties[prop]
                val propValue = jsonObj[prop]
                if (propType != null) {
                    propType.isProvided(propValue)
                } else {
                    propValue != null && propValue !is JsonNull
                }
            }
        }
    }

    /**
     * An enumeration type with a set of predefined options.
     */
    @Serializable
    @SerialName("enum")
    data class Enum(
        val className: String,
        val options: List<String>,
        val namespace: String? = null,
        val optionRequirements: Map<String, List<String>> = emptyMap()
    ) : DataType() {
        override fun isProvided(value: JsonElement?): Boolean {
            if (value == null || value is JsonNull) return false
            val content = (value as? JsonPrimitive)?.content ?: return false
            return options.contains(content)
        }
    }

    /**
     * A map of strings to a specific value type.
     */
    @Serializable
    @SerialName("map")
    data class MapType(val valueType: DataType) : DataType() {
        override fun isProvided(value: JsonElement?): Boolean {
            if (value == null || value is JsonNull) return false
            return (value as? JsonObject)?.isNotEmpty() ?: true
        }
    }

    /**
     * An unknown data type sent by a newer host/plugin.
     */
    @Serializable(with = DataTypeUnknownSerializer::class)
    @SerialName("unknown")
    data class Unknown(val rawType: String = "unknown") : DataType() {
        override fun isProvided(value: JsonElement?): Boolean = false
    }
}

object PrimitiveTypeSerializer : KSerializer<PrimitiveType> by createSafeEnumSerializer("PrimitiveType", PrimitiveType.UNKNOWN)

@Serializable(with = PrimitiveTypeSerializer::class)
enum class PrimitiveType {
    DOUBLE, FLOAT, LONG, INT, SHORT, BYTE, STRING, BOOLEAN, UNIT, ANY, UNKNOWN
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
    val type: DataType,
    val required: Boolean = false,
    val secret: Boolean = false,
    val constraints: ParameterConstraints? = null,
    /**
     * List of capability names that require this setting.
     * This allows UI to show which capabilities are locked behind this setting
     * without making the setting globally required for the plugin to load.
     */
    val requiredByCapabilities: List<String> = emptyList()
)

/**
 * The complete manifest of a plugin, describing its capabilities and requirements.
 *
 * This object is typically generated by KSP and bundled with the plugin.
 */
@Serializable
data class PluginManifest(
    val manifestVersion: String,
    val plugin: PluginInfo,
    val requirements: Requirements,
    val defaultParameters: Map<String, ParameterMetadata>? = null,
    val capabilities: List<Capability> = emptyList(),
    val actions: List<PluginAction> = emptyList(),
    val settings: Map<String, SettingMetadata>? = null,
    val changelog: Changelog? = null,
    val hasUpdateHandler: Boolean = false,
    val hasSetupHandler: Boolean = false,
    val hasMigrations: Boolean = false
)

@Serializable
data class PluginMigration(
    val fromVersion: String,
    val toVersion: String,
    val capabilityMigrations: List<CapabilityMigration> = emptyList(),
    val settingMigrations: List<SettingMigration> = emptyList(),
    val objectMigrations: List<ObjectMigration> = emptyList()
)

@Serializable
data class CapabilityMigration(
    val oldName: String,
    val newName: String?, // null means capability is removed/unsupported
    val isDropInReplacement: Boolean = false,
    val portMigrations: List<PortMigration> = emptyList()
)

@Serializable
data class PortMigration(
    val oldName: String,
    val newName: String? // null means port is removed
)

@Serializable
data class SettingMigration(
    val oldName: String,
    val newName: String? // null means setting is removed
)

@Serializable
data class ObjectMigration(
    val oldClassName: String,
    val newClassName: String?, // null means custom object is removed
    val propertyMigrations: List<PropertyMigration> = emptyList()
)

@Serializable
data class PropertyMigration(
    val oldName: String,
    val newName: String?
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

object OSSerializer : KSerializer<OS> by createSafeEnumSerializer("OS", OS.UNKNOWN)

@Serializable(with = OSSerializer::class)
enum class OS {
    LINUX, WINDOWS, MACOS, UNKNOWN
}

@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val supportedOs: List<OS> = emptyList()
)

@Serializable
data class Requirements(
    val minMemoryMb: Int,
    val minExecutionTimeMs: Int,
    val targetAppVersion: String? = null
)

object ParameterRoleSerializer : KSerializer<ParameterRole> by createSafeEnumSerializer("ParameterRole", ParameterRole.UNKNOWN)

@Serializable(with = ParameterRoleSerializer::class)
enum class ParameterRole {
    STANDARD, INPUT_LOCATION, OUTPUT_LOCATION, UNKNOWN
}

/**
 * Metadata for a parameter required by a capability.
 *
 * **File Location Checks**: For parameters with role [ParameterRole.INPUT_LOCATION] or [ParameterRole.OUTPUT_LOCATION],
 * the host application does **NOT** verify if the path actually exists on the filesystem or if a file will be overwritten.
 * It is the plugin's responsibility to handle file creation, check for existence, and manage overwriting as needed.
 */
@Serializable(with = ParameterMetadataSerializer::class)
data class ParameterMetadata(
    val defaultValue: JsonElement? = null,
    val description: String,
    val type: DataType,
    val constraints: ParameterConstraints? = null,
    val required: Boolean = false,
    val secret: Boolean = false,
    val semanticTypes: List<SemanticType> = emptyList(),
    val role: ParameterRole = ParameterRole.STANDARD,
    val autogeneratedPattern: String? = null,
    val isDestructive: Boolean = false
)

object ParameterMetadataSerializer : KSerializer<ParameterMetadata> {
    override val descriptor: SerialDescriptor = ParameterMetadataSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ParameterMetadata) {
        val surrogate = ParameterMetadataSurrogate(
            defaultValue = value.defaultValue,
            description = value.description,
            type = value.type,
            constraints = value.constraints,
            required = value.required,
            secret = value.secret,
            semanticTypes = value.semanticTypes,
            role = value.role,
            autogeneratedPattern = value.autogeneratedPattern,
            isDestructive = value.isDestructive
        )
        encoder.encodeSerializableValue(ParameterMetadataSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): ParameterMetadata {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This serializer only supports JSON")
        val element = input.decodeJsonElement() as JsonObject
        val hasSemanticTypes = "semanticTypes" in element
        val finalElement = if (!hasSemanticTypes) {
            val legacySemanticType = element["semanticType"]?.jsonPrimitive?.contentOrNull
            val parsedList = parseSemanticTypes(legacySemanticType).map { type ->
                buildJsonObject {
                    put("namespace", type.namespace)
                    put("name", type.name)
                    put("variant", type.variant)
                }
            }
            JsonObject(element.filterKeys { it != "semanticType" } + ("semanticTypes" to JsonArray(parsedList)))
        } else {
            JsonObject(element.filterKeys { it != "semanticType" })
        }
        val surrogate = input.json.decodeFromJsonElement(ParameterMetadataSurrogate.serializer(), finalElement)
        return ParameterMetadata(
            defaultValue = surrogate.defaultValue,
            description = surrogate.description,
            type = surrogate.type,
            constraints = surrogate.constraints,
            required = surrogate.required,
            secret = surrogate.secret,
            semanticTypes = surrogate.semanticTypes,
            role = surrogate.role,
            autogeneratedPattern = surrogate.autogeneratedPattern,
            isDestructive = surrogate.isDestructive
        )
    }
}

@Serializable
@SerialName("ParameterMetadata")
private class ParameterMetadataSurrogate(
    val defaultValue: JsonElement? = null,
    val description: String,
    val type: DataType,
    val constraints: ParameterConstraints? = null,
    val required: Boolean = false,
    val secret: Boolean = false,
    val semanticTypes: List<SemanticType> = emptyList(),
    val role: ParameterRole = ParameterRole.STANDARD,
    val autogeneratedPattern: String? = null,
    val isDestructive: Boolean = false
)

@Serializable(with = OutputMetadataSerializer::class)
data class OutputMetadata(
    val name: String,
    val description: String,
    val type: DataType,
    val semanticTypes: List<SemanticType> = emptyList()
)

object OutputMetadataSerializer : KSerializer<OutputMetadata> {
    override val descriptor: SerialDescriptor = OutputMetadataSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OutputMetadata) {
        val surrogate = OutputMetadataSurrogate(
            name = value.name,
            description = value.description,
            type = value.type,
            semanticTypes = value.semanticTypes
        )
        encoder.encodeSerializableValue(OutputMetadataSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): OutputMetadata {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This serializer only supports JSON")
        val element = input.decodeJsonElement() as JsonObject
        val hasSemanticTypes = "semanticTypes" in element
        val finalElement = if (!hasSemanticTypes) {
            val legacySemanticType = element["semanticType"]?.jsonPrimitive?.contentOrNull
            val parsedList = parseSemanticTypes(legacySemanticType).map { type ->
                buildJsonObject {
                    put("namespace", type.namespace)
                    put("name", type.name)
                    put("variant", type.variant)
                }
            }
            JsonObject(element.filterKeys { it != "semanticType" } + ("semanticTypes" to JsonArray(parsedList)))
        } else {
            JsonObject(element.filterKeys { it != "semanticType" })
        }
        val surrogate = input.json.decodeFromJsonElement(OutputMetadataSurrogate.serializer(), finalElement)
        return OutputMetadata(
            name = surrogate.name,
            description = surrogate.description,
            type = surrogate.type,
            semanticTypes = surrogate.semanticTypes
        )
    }
}

@Serializable
@SerialName("OutputMetadata")
private class OutputMetadataSurrogate(
    val name: String,
    val description: String,
    val type: DataType,
    val semanticTypes: List<SemanticType> = emptyList()
)

object CapabilityContextSerializer : KSerializer<CapabilityContext> by createSafeEnumSerializer("CapabilityContext", CapabilityContext.UNKNOWN)

@Serializable(with = CapabilityContextSerializer::class)
enum class CapabilityContext {
    ANY, FLOW_ONLY, STANDALONE_ONLY, UNKNOWN
}

@Serializable
data class FileAccess(
    val readsFiles: Boolean = false,
    val writesFiles: Boolean = false,
    val isDestructive: Boolean = false
)

/**
 * Metadata for a specific capability provided by the plugin.
 */
@Serializable(with = CapabilitySerializer::class)
data class Capability(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterMetadata>? = null,
    val returnType: DataType,
    val semanticTypes: List<SemanticType> = emptyList(),
    val outputs: List<OutputMetadata>? = null,
    val isPausable: Boolean = false,
    val isCancellable: Boolean = true,
    val context: CapabilityContext = CapabilityContext.ANY,
    val requiresSettings: List<String> = emptyList(),
    val fileAccess: FileAccess? = null
) {
    /**
     * Checks whether all settings required by this capability are provided and valid.
     */
    fun isReady(providedSettings: Map<String, kotlinx.serialization.json.JsonElement>, manifestSettings: Map<String, SettingMetadata>?): Boolean {
        if (requiresSettings.isEmpty()) return true
        if (manifestSettings == null) return false
        return requiresSettings.all { req ->
            val meta = manifestSettings[req] ?: return@all false
            val value = providedSettings[req] ?: return@all false
            meta.type.isProvided(value)
        }
    }
}

object CapabilitySerializer : KSerializer<Capability> {
    override val descriptor: SerialDescriptor = CapabilitySurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Capability) {
        val surrogate = CapabilitySurrogate(
            name = value.name,
            description = value.description,
            parameters = value.parameters,
            returnType = value.returnType,
            semanticTypes = value.semanticTypes,
            outputs = value.outputs,
            isPausable = value.isPausable,
            isCancellable = value.isCancellable,
            context = value.context,
            requiresSettings = value.requiresSettings,
            fileAccess = value.fileAccess
        )
        encoder.encodeSerializableValue(CapabilitySurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Capability {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This serializer only supports JSON")
        val element = input.decodeJsonElement() as JsonObject
        val hasSemanticTypes = "semanticTypes" in element
        val finalElement = if (!hasSemanticTypes) {
            val legacySemanticType = element["semanticType"]?.jsonPrimitive?.contentOrNull
            val parsedList = parseSemanticTypes(legacySemanticType).map { type ->
                buildJsonObject {
                    put("namespace", type.namespace)
                    put("name", type.name)
                    put("variant", type.variant)
                }
            }
            JsonObject(element.filterKeys { it != "semanticType" } + ("semanticTypes" to JsonArray(parsedList)))
        } else {
            JsonObject(element.filterKeys { it != "semanticType" })
        }
        val surrogate = input.json.decodeFromJsonElement(CapabilitySurrogate.serializer(), finalElement)
        return Capability(
            name = surrogate.name,
            description = surrogate.description,
            parameters = surrogate.parameters,
            returnType = surrogate.returnType,
            semanticTypes = surrogate.semanticTypes,
            outputs = surrogate.outputs,
            isPausable = surrogate.isPausable,
            isCancellable = surrogate.isCancellable,
            context = surrogate.context,
            requiresSettings = surrogate.requiresSettings,
            fileAccess = surrogate.fileAccess
        )
    }
}

@Serializable
@SerialName("Capability")
private class CapabilitySurrogate(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterMetadata>? = null,
    val returnType: DataType,
    val semanticTypes: List<SemanticType> = emptyList(),
    val outputs: List<OutputMetadata>? = null,
    val isPausable: Boolean = false,
    val isCancellable: Boolean = true,
    val context: CapabilityContext = CapabilityContext.ANY,
    val requiresSettings: List<String> = emptyList(),
    val fileAccess: FileAccess? = null
)

/**
 * Metadata for a custom action provided by the plugin.
 */
@Serializable
data class PluginAction(
    val name: String,
    val description: String,
    val functionName: String
)

