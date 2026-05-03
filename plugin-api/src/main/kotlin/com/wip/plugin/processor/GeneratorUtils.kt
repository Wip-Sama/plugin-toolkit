package com.wip.plugin.processor

import com.google.devtools.ksp.symbol.*
import com.wip.plugin.api.DataType
import com.wip.plugin.api.PrimitiveType
import com.squareup.kotlinpoet.ksp.toTypeName

object GeneratorUtils {
    fun mapKSTypeToDataType(ksType: KSType): DataType {
        val declaration = ksType.declaration
        val qualifiedName = declaration.qualifiedName?.asString() ?: ""
        
        return when (qualifiedName) {
            "kotlin.Double", "kotlin.Float" -> DataType.Primitive(PrimitiveType.DOUBLE)
            "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Long" -> DataType.Primitive(PrimitiveType.INT)
            "kotlin.String", "kotlin.Char" -> DataType.Primitive(PrimitiveType.STRING)
            "kotlin.Boolean" -> DataType.Primitive(PrimitiveType.BOOLEAN)
            "kotlin.Unit" -> DataType.Primitive(PrimitiveType.UNIT)
            "kotlinx.serialization.json.JsonElement", "kotlin.Any" -> DataType.Primitive(PrimitiveType.ANY)
            "kotlin.collections.List", "kotlin.collections.MutableList" -> {
                val elementType = ksType.arguments.firstOrNull()?.type?.resolve()
                if (elementType != null) {
                    DataType.Array(mapKSTypeToDataType(elementType))
                } else {
                    DataType.Primitive(PrimitiveType.ANY)
                }
            }
            else -> {
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
                    val options = declaration.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.classKind == ClassKind.ENUM_ENTRY }
                        .map { it.simpleName.asString() }
                        .toList()
                    DataType.Enum(qualifiedName, options)
                } else {
                    DataType.Object(qualifiedName)
                }
            }
        }
    }
}
