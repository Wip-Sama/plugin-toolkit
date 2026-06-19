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
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticTypes

object GeneratorUtils {
    data class OutputInfo(
        val name: String,
        val originalName: String,
        val type: DataType,
        val typeName: TypeName,
        val description: String,
        val semanticTypes: List<SemanticType>
    )

    fun mapKSTypeToDataType(ksType: KSType, visited: Set<String> = emptySet()): DataType {
        val declaration = ksType.declaration
        val qualifiedName = declaration.qualifiedName?.asString() ?: ""
        
        return when (qualifiedName) {
            "kotlin.Double", "kotlin.Float" -> DataType.Primitive(PrimitiveType.DOUBLE)
            "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Long" -> DataType.Primitive(PrimitiveType.INT)
            "kotlin.String", "kotlin.Char" -> DataType.Primitive(PrimitiveType.STRING)
            "kotlin.Boolean" -> DataType.Primitive(PrimitiveType.BOOLEAN)
            "kotlin.Unit" -> DataType.Primitive(PrimitiveType.UNIT)
            "kotlinx.serialization.json.JsonElement", "kotlin.Any" -> DataType.Primitive(PrimitiveType.ANY)
            "kotlin.collections.List", "kotlin.collections.MutableList", "kotlin.collections.Set", "kotlin.collections.MutableSet" -> {
                val elementType = ksType.arguments.firstOrNull()?.type?.resolve()
                if (elementType != null) {
                    DataType.Array(mapKSTypeToDataType(elementType, visited))
                } else {
                    DataType.Primitive(PrimitiveType.ANY)
                }
            }
            "kotlin.collections.Map", "kotlin.collections.MutableMap" -> {
                val valueType = ksType.arguments.getOrNull(1)?.type?.resolve()
                if (valueType != null) {
                    DataType.MapType(mapKSTypeToDataType(valueType, visited))
                } else {
                    DataType.MapType(DataType.Primitive(PrimitiveType.ANY))
                }
            }
            else -> {
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
                    val enumEntries = declaration.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.classKind == ClassKind.ENUM_ENTRY }
                        .toList()
                    val options = enumEntries.map { it.simpleName.asString() }
                    val optionRequirements = enumEntries.associate { entry ->
                        val reqAnn = entry.annotations.find { it.hasQualifiedName("org.wip.plugintoolkit.api.annotations.RequiresSetting") }
                        val settings = (reqAnn?.arguments?.find { it.name?.asString() == "settings" }?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        entry.simpleName.asString() to settings
                    }.filter { it.value.isNotEmpty() }
                    DataType.Enum(qualifiedName, options, null, optionRequirements)
                } else {
                    if (visited.contains(qualifiedName)) {
                        DataType.Object(qualifiedName)
                    } else {
                        val newVisited = visited + qualifiedName
                        val complexObjAnn = declaration.annotations.find { it.hasQualifiedName(ProcessorConstants.COMPLEX_OBJECT_ANNOTATION) }
                        val annId = complexObjAnn?.arguments?.find { it.name?.asString() == "id" }?.value as? String
                        val annDesc = complexObjAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String
                        val annVersion = complexObjAnn?.arguments?.find { it.name?.asString() == "version" }?.value as? Int
                        
                        val id = if (annId.isNullOrEmpty()) null else annId
                        val description = if (annDesc.isNullOrEmpty()) null else annDesc
                        val version = if (complexObjAnn != null) (annVersion ?: 1) else null
                        
                        val properties = if (declaration is KSClassDeclaration) {
                            declaration.getAllProperties().associate { prop ->
                                prop.simpleName.asString() to mapKSTypeToDataType(prop.type.resolve(), newVisited)
                            }
                        } else emptyMap()
                        DataType.Object(qualifiedName, id, description, version, null, properties)
                    }
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
            is DataType.MapType -> com.squareup.kotlinpoet.CodeBlock.of("%T(%L)",
                cnDataType.nestedClass("MapType"),
                generateDataTypeCode(dataType.valueType)
            )
            is DataType.Enum -> {
                val optionsList = dataType.options.joinToString { "\"$it\"" }
                if (dataType.optionRequirements.isEmpty()) {
                    com.squareup.kotlinpoet.CodeBlock.of("%T(%S, listOf(%L))",
                        cnDataType.nestedClass("Enum"),
                        dataType.className,
                        optionsList
                    )
                } else {
                    val reqMapStr = dataType.optionRequirements.entries.joinToString(", ") { entry ->
                        "\"${entry.key}\" to listOf(${entry.value.joinToString { "\"$it\"" }})"
                    }
                    com.squareup.kotlinpoet.CodeBlock.of("%T(%S, listOf(%L), null, mapOf(%L))",
                        cnDataType.nestedClass("Enum"),
                        dataType.className,
                        optionsList,
                        reqMapStr
                    )
                }
            }
            is DataType.Object -> {
                val idStr = dataType.id?.let { "\"$it\"" } ?: "null"
                val descStr = dataType.description?.let { "\"$it\"" } ?: "null"
                val verStr = dataType.version?.toString() ?: "null"
                
                if (dataType.properties.isEmpty()) {
                    com.squareup.kotlinpoet.CodeBlock.of("%T(%S, %L, %L, %L)",
                        cnDataType.nestedClass("Object"),
                        dataType.className,
                        idStr, descStr, verStr
                    )
                } else {
                    val propsCode = com.squareup.kotlinpoet.CodeBlock.builder()
                    propsCode.add("mapOf(\n")
                    propsCode.indent()
                    val entries = dataType.properties.entries.toList()
                    entries.forEachIndexed { index, entry ->
                        propsCode.add("%S to %L", entry.key, generateDataTypeCode(entry.value))
                        if (index < entries.size - 1) propsCode.add(",\n") else propsCode.add("\n")
                    }
                    propsCode.unindent()
                    propsCode.add(")")
                    com.squareup.kotlinpoet.CodeBlock.of("%T(%S, %L, %L, %L, null, %L)",
                        cnDataType.nestedClass("Object"),
                        dataType.className,
                        idStr, descStr, verStr,
                        propsCode.build()
                    )
                }
            }
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
            val semTypesVal = (funcOutputAnn.arguments.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val semanticTypesList = semTypesVal.flatMap { parseSemanticTypes(it) }
            return listOf(
                OutputInfo(
                    name = name.ifEmpty { "result" },
                    originalName = "result",
                    type = mapKSTypeToDataType(returnTypeKS),
                    typeName = returnTypeName,
                    description = desc,
                    semanticTypes = semanticTypesList
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
                    val semTypesVal = (propAnn?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val semanticTypesList = semTypesVal.flatMap { parseSemanticTypes(it) }
                    val propTypeKS = prop.type.resolve()
                    
                    OutputInfo(
                        name = name.ifEmpty { prop.simpleName.asString() },
                        originalName = prop.simpleName.asString(),
                        type = mapKSTypeToDataType(propTypeKS),
                        typeName = propTypeKS.toTypeName(),
                        description = desc,
                        semanticTypes = semanticTypesList
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
                semanticTypes = emptyList()
            )
        )
    }
}
