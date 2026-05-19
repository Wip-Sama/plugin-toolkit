package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType

object GeneratorUtils {
    data class OutputInfo(
        val name: String,
        val originalName: String,
        val type: DataType,
        val typeName: TypeName,
        val description: String,
        val semanticType: String?
    )

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

    fun KSAnnotation.hasQualifiedName(name: String): Boolean {
        return this.annotationType.resolve().declaration.qualifiedName?.asString() == name
    }

    fun generateDataTypeCode(dataType: DataType): com.squareup.kotlinpoet.CodeBlock {
        val cnDataType = com.squareup.kotlinpoet.ClassName("org.wip.plugintoolkit.api", "DataType")
        val cnPrimitiveType = com.squareup.kotlinpoet.ClassName("org.wip.plugintoolkit.api", "PrimitiveType")
        return when (dataType) {
            is DataType.Primitive -> com.squareup.kotlinpoet.CodeBlock.of("%T(%T.%L)", 
                cnDataType.nestedClass("Primitive"),
                cnPrimitiveType,
                dataType.primitiveType.name
            )
            is DataType.Array -> com.squareup.kotlinpoet.CodeBlock.of("%T(%L)",
                cnDataType.nestedClass("Array"),
                generateDataTypeCode(dataType.items)
            )
            is DataType.Enum -> {
                val optionsList = dataType.options.joinToString { "\"$it\"" }
                com.squareup.kotlinpoet.CodeBlock.of("%T(%S, listOf(%L))",
                    cnDataType.nestedClass("Enum"),
                    dataType.className,
                    optionsList
                )
            }
            is DataType.Object -> com.squareup.kotlinpoet.CodeBlock.of("%T(%S)",
                cnDataType.nestedClass("Object"),
                dataType.className
            )
        }
    }

    fun getCapabilityOutputs(func: KSFunctionDeclaration): List<OutputInfo> {
        val returnTypeKS = func.returnType?.resolve() ?: return emptyList()
        val returnTypeName = returnTypeKS.toTypeName()
        
        if (returnTypeName.toString() == "kotlin.Unit") {
            return emptyList()
        }

        val funcOutputAnn = func.annotations.find { 
            it.hasQualifiedName("org.wip.plugintoolkit.api.annotations.CapabilityOutput") 
        }

        if (funcOutputAnn != null) {
            val name = funcOutputAnn.arguments.find { it.name?.asString() == "name" }?.value as? String ?: ""
            val desc = funcOutputAnn.arguments.find { it.name?.asString() == "description" }?.value as? String ?: ""
            val sem = funcOutputAnn.arguments.find { it.name?.asString() == "semanticType" }?.value as? String ?: ""
            return listOf(
                OutputInfo(
                    name = name.ifEmpty { "result" },
                    originalName = "result",
                    type = mapKSTypeToDataType(returnTypeKS),
                    typeName = returnTypeName,
                    description = desc,
                    semanticType = sem.ifEmpty { null }
                )
            )
        }

        val declaration = returnTypeKS.declaration
        val isDataClass = declaration is KSClassDeclaration && declaration.modifiers.contains(Modifier.DATA)

        if (isDataClass) {
            val classDecl = declaration as KSClassDeclaration
            val properties = classDecl.getAllProperties().toList()
            val hasAnnotatedProperties = properties.any { prop ->
                prop.annotations.any { it.hasQualifiedName("org.wip.plugintoolkit.api.annotations.CapabilityOutput") }
            }

            if (hasAnnotatedProperties) {
                return properties.map { prop ->
                    val propAnn = prop.annotations.find { 
                        it.hasQualifiedName("org.wip.plugintoolkit.api.annotations.CapabilityOutput") 
                    }
                    val name = propAnn?.arguments?.find { it.name?.asString() == "name" }?.value as? String ?: ""
                    val desc = propAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                    val sem = propAnn?.arguments?.find { it.name?.asString() == "semanticType" }?.value as? String ?: ""
                    val propTypeKS = prop.type.resolve()
                    
                    OutputInfo(
                        name = name.ifEmpty { prop.simpleName.asString() },
                        originalName = prop.simpleName.asString(),
                        type = mapKSTypeToDataType(propTypeKS),
                        typeName = propTypeKS.toTypeName(),
                        description = desc,
                        semanticType = sem.ifEmpty { null }
                    )
                }
            }
        }

        // Default to a single "result" output
        return listOf(
            OutputInfo(
                name = "result",
                originalName = "result",
                type = mapKSTypeToDataType(returnTypeKS),
                typeName = returnTypeName,
                description = "",
                semanticType = null
            )
        )
    }
}
