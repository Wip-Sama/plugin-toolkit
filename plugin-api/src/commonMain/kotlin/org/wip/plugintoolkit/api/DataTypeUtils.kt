package org.wip.plugintoolkit.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

sealed class CompatibilityResult {
    object Compatible : CompatibilityResult()
    data class Warning(val message: String) : CompatibilityResult()
    object Incompatible : CompatibilityResult()

    val isCompatible: Boolean get() = this is Compatible || this is Warning
}

fun interface TypeConverter {
    fun canConvert(source: DataType, target: DataType): Boolean
}

object TypeConverterRegistry {
    private val converters = mutableListOf<TypeConverter>()

    fun register(converter: TypeConverter) {
        converters.add(converter)
    }

    fun canConvert(source: DataType, target: DataType): Boolean {
        return converters.any { it.canConvert(source, target) }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.toDataType(): DataType {
    return when (this.kind) {
        PrimitiveKind.DOUBLE -> DataType.Primitive(PrimitiveType.DOUBLE)
        PrimitiveKind.FLOAT -> DataType.Primitive(PrimitiveType.FLOAT)
        PrimitiveKind.LONG -> DataType.Primitive(PrimitiveType.LONG)
        PrimitiveKind.INT -> DataType.Primitive(PrimitiveType.INT)
        PrimitiveKind.SHORT -> DataType.Primitive(PrimitiveType.SHORT)
        PrimitiveKind.BYTE -> DataType.Primitive(PrimitiveType.BYTE)

        PrimitiveKind.STRING, PrimitiveKind.CHAR -> DataType.Primitive(PrimitiveType.STRING)
        PrimitiveKind.BOOLEAN -> DataType.Primitive(PrimitiveType.BOOLEAN)
        StructureKind.LIST -> {
            val elementDescriptor = this.getElementDescriptor(0)
            DataType.Array(elementDescriptor.toDataType())
        }

        StructureKind.MAP -> {
            val valueDescriptor = this.getElementDescriptor(1)
            DataType.MapType(valueDescriptor.toDataType())
        }
        PolymorphicKind.SEALED, PolymorphicKind.OPEN -> DataType.Object(this.serialName)
        SerialKind.ENUM -> {
            val options = (0 until this.elementsCount).map { this.getElementName(it) }
            DataType.Enum(this.serialName, options)
        }

        else -> {
            val unitName = serializer<Unit>().descriptor.serialName
            val jsonElementName = serializer<JsonElement>().descriptor.serialName

            when (this.serialName) {
                unitName -> DataType.Primitive(PrimitiveType.UNIT)
                jsonElementName -> DataType.Primitive(PrimitiveType.ANY)
                "kotlin.Any" -> DataType.Primitive(PrimitiveType.ANY)
                else -> DataType.Object(this.serialName)
            }
        }
    }
}

inline fun <reified T> getDataType(): DataType {
    return serializer<T>().descriptor.toDataType()
}

/**
 * Checks if this [DataType] is compatible with and can connect to another [DataType].
 */
fun DataType.isCompatibleWith(other: DataType): Boolean {
    if (this == other) return true

    // Wildcard support: ANY is compatible with any type
    if (this is DataType.Primitive && this.primitiveType == PrimitiveType.ANY) return true
    if (other is DataType.Primitive && other.primitiveType == PrimitiveType.ANY) return true

    return when (this) {
        is DataType.Primitive -> {
            other is DataType.Primitive && this.primitiveType == other.primitiveType
        }

        is DataType.Array -> {
            other is DataType.Array && this.items.isCompatibleWith(other.items)
        }

        is DataType.MapType -> {
            other is DataType.MapType && this.valueType.isCompatibleWith(other.valueType)
        }

        is DataType.Object -> {
            if (other !is DataType.Object) return false
            if (this.className != other.className) return false
            if (this.properties.isEmpty() && other.properties.isEmpty()) return true
            this.properties.size == other.properties.size && this.properties.all { (key, type) ->
                val otherType = other.properties[key]
                otherType != null && type.isCompatibleWith(otherType)
            }
        }

        is DataType.Enum -> {
            other is DataType.Enum && this.className == other.className
        }
    }
}

/**
 * Checks if two semantic types are compatible for a connection.
 * Returns a CompatibilityResult indicating Compatible, Warning, or Incompatible.
 */
fun checkSemanticCompatibility(source: List<SemanticType>, target: List<SemanticType>, lenient: Boolean = true): CompatibilityResult {
    if (source.isEmpty() || target.isEmpty()) {
        return CompatibilityResult.Warning("Semantic types are missing on one or both endpoints. Data mismatch may occur.")
    }
    for (s in source) {
        for (t in target) {
            val nsMatches = if (s.namespace == null && t.namespace == null) {
                true
            } else {
                s.namespace.equals(t.namespace, ignoreCase = true)
            }
            if (nsMatches) {
                val sName = if (s.name == "*") null else s.name
                val tName = if (t.name == "*") null else t.name
                val nameMatches = when {
                    sName.equals(tName, ignoreCase = true) -> true
                    tName == null -> true // Target accepts anything
                    sName == null -> lenient // Source provides anything
                    else -> false
                }

                if (nameMatches) {
                    val sVar = if (s.variant == "*") null else s.variant
                    val tVar = if (t.variant == "*") null else t.variant
                    val variantMatches = when {
                        sVar.equals(tVar, ignoreCase = true) -> true // Identity
                        tVar == null -> true // Generalization
                        sVar == null -> lenient // Specialization (lenient mode)
                        else -> false
                    }
                    if (variantMatches) return CompatibilityResult.Compatible
                }
            }
        }
    }
    return CompatibilityResult.Incompatible
}

/**
 * Legacy check for boolean result.
 */
@Deprecated("Use checkSemanticCompatibility instead", ReplaceWith("checkSemanticCompatibility(source, target, lenient).isCompatible"))
fun isSemanticTypeCompatible(source: List<SemanticType>, target: List<SemanticType>, lenient: Boolean = true): Boolean {
    return checkSemanticCompatibility(source, target, lenient).isCompatible
}

@Deprecated("Use List<SemanticType> comparison instead", ReplaceWith("isSemanticTypeCompatible(parseSemanticTypes(source), parseSemanticTypes(target))"))
fun isSemanticTypeCompatible(source: String?, target: String?): Boolean {
    val srcList = parseSemanticTypes(source)
    val tgtList = parseSemanticTypes(target)
    return isSemanticTypeCompatible(srcList, tgtList, lenient = true)
}

fun DataType.format(): String {
    return when (this) {
        is DataType.Primitive -> this.primitiveType.name
        is DataType.Array -> "Array<${this.items.format()}>"
        is DataType.MapType -> "Map<String, ${this.valueType.format()}>"
        is DataType.Object -> this.className.substringAfterLast('.')
        is DataType.Enum -> this.className.substringAfterLast('.')
    }
}

private fun isIterable(type: DataType): Boolean {
    if (type is DataType.Array) return true
    if (type is DataType.Object) {
        val name = type.className.substringAfterLast('.').lowercase()
        return name.contains("list") || name.contains("array") || name.contains("tuple") ||
                name.contains("pair") || name.contains("triple") || name.contains("set") ||
                name.contains("collection") || name.contains("iterable")
    }
    return false
}

/**
 * Checks if a value of this [DataType] can be automatically converted to another [DataType].
 */
fun DataType.canConvert(other: DataType): Boolean {
    if (this == other) return false
    if (this.isCompatibleWith(other)) return false

    val isThisIterable = isIterable(this)
    val isOtherIterable = isIterable(other)

    // Rule 2: Iterable to Iterable
    if (isThisIterable && isOtherIterable) {
        if (this is DataType.Array && other is DataType.Array) {
            return this.items.isCompatibleWith(other.items) || this.items.canConvert(other.items)
        }
        return true // Fallback for generic iterables
    }

    // Rule 3: Single element to Iterable
    if (isOtherIterable && !isThisIterable) {
        if (other is DataType.Array) {
            return this.isCompatibleWith(other.items) || this.canConvert(other.items)
        }
        return true
    }

    // Rule 1: Numeric primitives & String conversions
    if (this is DataType.Primitive && other is DataType.Primitive) {
        val sType = this.primitiveType
        val tType = other.primitiveType
        val numerics = setOf(PrimitiveType.INT, PrimitiveType.DOUBLE, PrimitiveType.LONG, PrimitiveType.FLOAT, PrimitiveType.SHORT, PrimitiveType.BYTE)
        if (sType in numerics && tType in numerics) {
            return true
        }
        // Allow String to Numeric/Boolean
        if (sType == PrimitiveType.STRING && (tType in numerics || tType == PrimitiveType.BOOLEAN)) {
            return true
        }
        // Allow Numeric/Boolean to String
        if ((sType in numerics || sType == PrimitiveType.BOOLEAN) && tType == PrimitiveType.STRING) {
            return true
        }
    }

    // Rule 4: Dynamic Conversion Registry
    return TypeConverterRegistry.canConvert(this, other)
}
