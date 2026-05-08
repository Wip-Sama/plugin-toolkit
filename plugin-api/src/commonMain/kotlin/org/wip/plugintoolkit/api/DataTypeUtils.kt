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
