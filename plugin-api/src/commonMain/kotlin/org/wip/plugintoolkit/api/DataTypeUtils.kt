package org.wip.plugintoolkit.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

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
        is DataType.MapType -> {
            other is DataType.MapType && this.valueType.isCompatibleWith(other.valueType)
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
 * If either semantic type list is empty, they are universally compatible.
 * Otherwise, at least one semantic type in the source must satisfy one in the target.
 */
fun isSemanticTypeCompatible(source: List<SemanticType>, target: List<SemanticType>, lenient: Boolean = true): Boolean {
    if (source.isEmpty() || target.isEmpty()) return true
    for (s in source) {
        for (t in target) {
            val nsMatches = if (s.namespace == null && t.namespace == null) {
                true
            } else {
                s.namespace.equals(t.namespace, ignoreCase = true)
            }
            if (nsMatches) {
                if (s.name.equals(t.name, ignoreCase = true)) {
                    val sVar = if (s.variant == "*") null else s.variant
                    val tVar = if (t.variant == "*") null else t.variant
                    val variantMatches = when {
                        sVar.equals(tVar, ignoreCase = true) -> true // Identity
                        tVar == null -> true // Generalization
                        sVar == null -> lenient // Specialization (lenient mode)
                        else -> false
                    }
                    if (variantMatches) return true
                }
            }
        }
    }
    return false
}

/**
 * Checks if two semantic types are compatible for a connection.
 *
 * If either semantic type is null or blank, they are compatible (lenient matching).
 * If both are specified, they must match.
 */
@Deprecated("Use List<SemanticType> comparison instead", ReplaceWith("isSemanticTypeCompatible(parseSemanticTypes(source), parseSemanticTypes(target))"))
fun isSemanticTypeCompatible(source: String?, target: String?): Boolean {
    val srcList = parseSemanticTypes(source)
    val tgtList = parseSemanticTypes(target)
    return isSemanticTypeCompatible(srcList, tgtList, lenient = true)
}

/**
 * Formats a [DataType] into a user-friendly string representation.
 */
fun DataType.format(): String {
    return when (this) {
        is DataType.Primitive -> this.primitiveType.name
        is DataType.Array -> "Array<${this.items.format()}>"
        is DataType.MapType -> "Map<String, ${this.valueType.format()}>"
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

    // Rule 1: Numeric primitives & String conversions
    if (this is DataType.Primitive && other is DataType.Primitive) {
        val sType = this.primitiveType
        val tType = other.primitiveType
        if ((sType == PrimitiveType.INT && tType == PrimitiveType.DOUBLE) ||
            (sType == PrimitiveType.DOUBLE && tType == PrimitiveType.INT)) {
            return true
        }
        // Allow String to Int, Double, Boolean
        if (sType == PrimitiveType.STRING && (tType == PrimitiveType.INT || tType == PrimitiveType.DOUBLE || tType == PrimitiveType.BOOLEAN)) {
            return true
        }
        // Allow Int, Double, Boolean to String
        if ((sType == PrimitiveType.INT || sType == PrimitiveType.DOUBLE || sType == PrimitiveType.BOOLEAN) && tType == PrimitiveType.STRING) {
            return true
        }
    }

    return false
}



