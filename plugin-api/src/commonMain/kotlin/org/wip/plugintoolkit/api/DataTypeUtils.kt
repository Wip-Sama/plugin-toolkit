package org.wip.plugintoolkit.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer

import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.toDataType(): DataType {
    return when (this.kind) {
        PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT -> DataType.Primitive(PrimitiveType.DOUBLE)
        PrimitiveKind.INT, PrimitiveKind.SHORT, PrimitiveKind.BYTE, PrimitiveKind.LONG -> DataType.Primitive(PrimitiveType.INT)
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> DataType.Primitive(PrimitiveType.STRING)
        PrimitiveKind.BOOLEAN -> DataType.Primitive(PrimitiveType.BOOLEAN)
        StructureKind.LIST -> {
            val elementDescriptor = this.getElementDescriptor(0)
            DataType.Array(elementDescriptor.toDataType())
        }
        StructureKind.MAP -> DataType.Object(this.serialName)
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
 *
 * Compatibility rules:
 * 1. Two identical datatypes are compatible.
 * 2. Wildcard [PrimitiveType.ANY] is compatible with any datatype.
 * 3. Arrays are compatible if their item types are compatible.
 * 4. Objects are compatible if their class names are equal.
 * 5. Enums are compatible if their class names are equal (options can differ or be in different order).
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
        is DataType.Object -> {
            other is DataType.Object && this.className == other.className
        }
        is DataType.Enum -> {
            other is DataType.Enum && this.className == other.className
        }
    }
}

/**
 * Checks if two semantic types are compatible for a connection.
 *
 * If either semantic type is null or blank, they are compatible (lenient matching).
 * If both are specified, they must match (case-insensitive).
 */
fun isSemanticTypeCompatible(source: String?, target: String?): Boolean {
    if (source.isNullOrBlank() || target.isNullOrBlank()) return true
    return source.equals(target, ignoreCase = true)
}

/**
 * Formats a [DataType] into a user-friendly string representation.
 */
fun DataType.format(): String {
    return when (this) {
        is DataType.Primitive -> this.primitiveType.name
        is DataType.Array -> "Array<${this.items.format()}>"
        is DataType.Object -> this.className.substringAfterLast('.')
        is DataType.Enum -> this.className.substringAfterLast('.')
    }
}

/**
 * Checks if a value of this [DataType] can be automatically converted to another [DataType].
 *
 * Conversion rules:
 * 1. Convert between numeric primitives (INT and DOUBLE).
 * 2. Convert between list/array and tuple/pair/triple.
 */
fun DataType.canConvert(other: DataType): Boolean {
    if (this == other) return false

    // Rule 2: List / Tuple conversions & Rule 3: Single element (Any / Primitive / Object) to List / Tuple
    val thisStr = this.format().lowercase()
    val otherStr = other.format().lowercase()

    val isThisListOrTuple = thisStr.contains("list") || thisStr.contains("array") || thisStr.contains("tuple") || thisStr.contains("pair") || thisStr.contains("triple")
    val isOtherListOrTuple = otherStr.contains("list") || otherStr.contains("array") || otherStr.contains("tuple") || otherStr.contains("pair") || otherStr.contains("triple")

    if (isThisListOrTuple && isOtherListOrTuple) {
        return true
    }

    if (isOtherListOrTuple && !isThisListOrTuple) {
        return true
    }

    if (this.isCompatibleWith(other)) return false

    // Rule 1: Numeric primitives
    if (this is DataType.Primitive && other is DataType.Primitive) {
        val sType = this.primitiveType
        val tType = other.primitiveType
        if ((sType == PrimitiveType.INT && tType == PrimitiveType.DOUBLE) ||
            (sType == PrimitiveType.DOUBLE && tType == PrimitiveType.INT)) {
            return true
        }
    }

    return false
}



