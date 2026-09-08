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
        val semanticTypes: List<SemanticType>,
        val isAdvanced: Boolean = false,
        val condition: org.wip.plugintoolkit.api.ConditionGroup? = null
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
                        val reqAnn =
                            entry.annotations.find { it.hasQualifiedName(ProcessorConstants.REQUIRES_SETTING_ANNOTATION) }
                        val settings =
                            (reqAnn?.arguments?.find { it.name?.asString() == "settings" }?.value as? List<*>)?.filterIsInstance<String>()
                                ?: emptyList()
                        entry.simpleName.asString() to settings
                    }.filter { it.value.isNotEmpty() }
                    val optionLockRequirements = enumEntries.associate { entry ->
                        val lockAnn =
                            entry.annotations.find { it.hasQualifiedName(ProcessorConstants.REQUIRES_LOCK_ANNOTATION) }
                        val locks =
                            (lockAnn?.arguments?.find { it.name?.asString() == "locks" }?.value as? List<*>)?.filterIsInstance<String>()
                                ?: emptyList()
                        entry.simpleName.asString() to locks
                    }.filter { it.value.isNotEmpty() }
                    DataType.Enum(qualifiedName, options, null, optionRequirements, optionLockRequirements)
                } else {
                    if (visited.contains(qualifiedName)) {
                        DataType.Object(qualifiedName)
                    } else {
                        val newVisited = visited + qualifiedName
                        val complexObjAnn =
                            declaration.annotations.find { it.hasQualifiedName(ProcessorConstants.COMPLEX_OBJECT_ANNOTATION) }
                        val annId = complexObjAnn?.arguments?.find { it.name?.asString() == "id" }?.value as? String
                        val annDesc =
                            complexObjAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String
                        val annVersion =
                            complexObjAnn?.arguments?.find { it.name?.asString() == "version" }?.value as? Int

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
            is DataType.Primitive -> com.squareup.kotlinpoet.CodeBlock.of(
                "%T(%T.%L)",
                cnDataType.nestedClass("Primitive"),
                cnPrimitiveType,
                dataType.primitiveType.name
            )

            is DataType.Array -> com.squareup.kotlinpoet.CodeBlock.of(
                "%T(%L)",
                cnDataType.nestedClass("Array"),
                generateDataTypeCode(dataType.items)
            )

            is DataType.MapType -> com.squareup.kotlinpoet.CodeBlock.of(
                "%T(%L)",
                cnDataType.nestedClass("MapType"),
                generateDataTypeCode(dataType.valueType)
            )

            is DataType.Enum -> {
                val optionsList = dataType.options.joinToString { "\"$it\"" }
                if (dataType.optionRequirements.isEmpty() && dataType.optionLockRequirements.isEmpty()) {
                    com.squareup.kotlinpoet.CodeBlock.of(
                        "%T(%S, listOf(%L))",
                        cnDataType.nestedClass("Enum"),
                        dataType.className,
                        optionsList
                    )
                } else {
                    val reqMapStr = if (dataType.optionRequirements.isNotEmpty()) {
                        "mapOf(" + dataType.optionRequirements.entries.joinToString(", ") { entry ->
                            "\"${entry.key}\" to listOf(${entry.value.joinToString { "\"$it\"" }})"
                        } + ")"
                    } else "emptyMap()"
                    
                    val lockReqMapStr = if (dataType.optionLockRequirements.isNotEmpty()) {
                        "mapOf(" + dataType.optionLockRequirements.entries.joinToString(", ") { entry ->
                            "\"${entry.key}\" to listOf(${entry.value.joinToString { "\"$it\"" }})"
                        } + ")"
                    } else "emptyMap()"

                    com.squareup.kotlinpoet.CodeBlock.of(
                        "%T(%S, listOf(%L), null, %L, %L)",
                        cnDataType.nestedClass("Enum"),
                        dataType.className,
                        optionsList,
                        reqMapStr,
                        lockReqMapStr
                    )
                }
            }

            is DataType.Object -> {
                val idStr = dataType.id?.let { "\"$it\"" } ?: "null"
                val descStr = dataType.description?.let { "\"$it\"" } ?: "null"
                val verStr = dataType.version?.toString() ?: "null"

                if (dataType.properties.isEmpty()) {
                    com.squareup.kotlinpoet.CodeBlock.of(
                        "%T(%S, %L, %L, %L)",
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
                    com.squareup.kotlinpoet.CodeBlock.of(
                        "%T(%S, %L, %L, %L, null, %L)",
                        cnDataType.nestedClass("Object"),
                        dataType.className,
                        idStr, descStr, verStr,
                        propsCode.build()
                    )
                }
            }

            is DataType.Unknown -> com.squareup.kotlinpoet.CodeBlock.of(
                "%T(%S)",
                cnDataType.nestedClass("Unknown"),
                dataType.rawType
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
            it.hasQualifiedName(ProcessorConstants.CAPABILITY_RESULT_ANNOTATION)
        }

        if (funcOutputAnn != null) {
            val name = funcOutputAnn.arguments.find { it.name?.asString() == "name" }?.value as? String ?: ""
            val desc = funcOutputAnn.arguments.find { it.name?.asString() == "description" }?.value as? String ?: ""
            val semTypesVal =
                (funcOutputAnn.arguments.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
            val semanticTypesList = semTypesVal.flatMap { parseSemanticTypes(it) }
            val isAdvanced = funcOutputAnn.arguments.find { it.name?.asString() == "isAdvanced" }?.value as? Boolean ?: false
            val conditionGroup = extractConditionGroup(func)
            return listOf(
                OutputInfo(
                    name = name.ifEmpty { "result" },
                    originalName = "result",
                    type = mapKSTypeToDataType(returnTypeKS),
                    typeName = returnTypeName,
                    description = desc,
                    semanticTypes = semanticTypesList,
                    isAdvanced = isAdvanced,
                    condition = conditionGroup
                )
            )
        }

        val declaration = returnTypeKS.declaration
        val isDataClass = declaration is KSClassDeclaration && declaration.modifiers.contains(Modifier.DATA)

        if (isDataClass) {
            val classDecl = declaration as KSClassDeclaration
            val properties = classDecl.getAllProperties().toList()
            val hasAnnotatedProperties = properties.any { prop ->
                prop.annotations.any { it.hasQualifiedName(ProcessorConstants.CAPABILITY_RESULT_ANNOTATION) }
            }

            if (hasAnnotatedProperties) {
                return properties.map { prop ->
                    val propAnn = prop.annotations.find {
                        it.hasQualifiedName(ProcessorConstants.CAPABILITY_RESULT_ANNOTATION)
                    }
                    val name = propAnn?.arguments?.find { it.name?.asString() == "name" }?.value as? String ?: ""
                    val desc = propAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                    val semTypesVal =
                        (propAnn?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                            ?: emptyList()
                    val semanticTypesList = semTypesVal.flatMap { parseSemanticTypes(it) }
                    val propTypeKS = prop.type.resolve()
                    val isAdvanced = propAnn?.arguments?.find { it.name?.asString() == "isAdvanced" }?.value as? Boolean ?: false
                    val conditionGroup = extractConditionGroup(prop)

                    OutputInfo(
                        name = name.ifEmpty { prop.simpleName.asString() },
                        originalName = prop.simpleName.asString(),
                        type = mapKSTypeToDataType(propTypeKS),
                        typeName = propTypeKS.toTypeName(),
                        description = desc,
                        semanticTypes = semanticTypesList,
                        isAdvanced = isAdvanced,
                        condition = conditionGroup
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
                semanticTypes = emptyList(),
                isAdvanced = false,
                condition = null
            )
        )
    }

    fun parseDependsOnAnnotation(ann: KSAnnotation): org.wip.plugintoolkit.api.ParameterCondition {
        val param = ann.arguments.find { it.name?.asString() == "param" }?.value as? String ?: ""
        val setting = ann.arguments.find { it.name?.asString() == "setting" }?.value as? String ?: ""
        val lock = ann.arguments.find { it.name?.asString() == "lock" }?.value as? String ?: ""
        val opArg = ann.arguments.find { it.name?.asString() == "operator" }?.value
        val opName = when (opArg) {
            is com.google.devtools.ksp.symbol.KSClassDeclaration -> opArg.simpleName.asString()
            is KSType -> opArg.declaration.simpleName.asString()
            else -> opArg?.toString()?.substringAfterLast('.')?.substringAfterLast(':')?.trim() ?: "EQUALS"
        }
        val op = try {
            org.wip.plugintoolkit.api.ConditionOperator.valueOf(opName)
        } catch (e: Exception) {
            org.wip.plugintoolkit.api.ConditionOperator.EQUALS
        }
        val value = ann.arguments.find { it.name?.asString() == "value" }?.value as? String ?: ""
        val values = (ann.arguments.find { it.name?.asString() == "values" }?.value as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()

        val source = when {
            setting.isNotBlank() -> org.wip.plugintoolkit.api.ConditionSource.SETTING
            lock.isNotBlank() -> org.wip.plugintoolkit.api.ConditionSource.LOCK
            else -> org.wip.plugintoolkit.api.ConditionSource.PARAMETER
        }
        val target = when {
            setting.isNotBlank() -> setting
            lock.isNotBlank() -> lock
            else -> param
        }
        return org.wip.plugintoolkit.api.ParameterCondition(
            source = source,
            target = target,
            operator = op,
            value = value,
            values = values
        )
    }

    fun extractConditionGroup(annotated: com.google.devtools.ksp.symbol.KSAnnotated): org.wip.plugintoolkit.api.ConditionGroup? {
        val directDependsOn = annotated.annotations
            .filter { it.hasQualifiedName(ProcessorConstants.DEPENDS_ON_ANNOTATION) }
            .map { parseDependsOnAnnotation(it) }
            .toList()

        val dependsOnAnyAnn = annotated.annotations.find {
            it.hasQualifiedName(ProcessorConstants.DEPENDS_ON_ANY_ANNOTATION)
        }
        val dependsOnAllAnn = annotated.annotations.find {
            it.hasQualifiedName(ProcessorConstants.DEPENDS_ON_ALL_ANNOTATION)
        }

        if (dependsOnAnyAnn != null) {
            val nested = (dependsOnAnyAnn.arguments.find { it.name?.asString() == "conditions" }?.value as? List<*>)
                ?.filterIsInstance<KSAnnotation>()?.map { parseDependsOnAnnotation(it) } ?: emptyList()
            val allConditions = directDependsOn + nested
            if (allConditions.isNotEmpty()) {
                return org.wip.plugintoolkit.api.ConditionGroup(allConditions, isOr = true)
            }
        }

        if (dependsOnAllAnn != null) {
            val nested = (dependsOnAllAnn.arguments.find { it.name?.asString() == "conditions" }?.value as? List<*>)
                ?.filterIsInstance<KSAnnotation>()?.map { parseDependsOnAnnotation(it) } ?: emptyList()
            val allConditions = directDependsOn + nested
            if (allConditions.isNotEmpty()) {
                return org.wip.plugintoolkit.api.ConditionGroup(allConditions, isOr = false)
            }
        }

        if (directDependsOn.isNotEmpty()) {
            return org.wip.plugintoolkit.api.ConditionGroup(directDependsOn, isOr = false)
        }

        return null
    }

    fun generateConditionGroupCode(group: org.wip.plugintoolkit.api.ConditionGroup?): com.squareup.kotlinpoet.CodeBlock {
        if (group == null || group.conditions.isEmpty()) {
            return com.squareup.kotlinpoet.CodeBlock.of("null")
        }
        val cnGroup = ProcessorConstants.CN_CONDITION_GROUP
        val cnCondition = ProcessorConstants.CN_PARAMETER_CONDITION
        val cnSource = ProcessorConstants.CN_CONDITION_SOURCE
        val cnOperator = ProcessorConstants.CN_CONDITION_OPERATOR

        val builder = com.squareup.kotlinpoet.CodeBlock.builder()
        builder.add("%T(conditions = listOf(\n", cnGroup)
        builder.indent()
        group.conditions.forEachIndexed { index, cond ->
            val valuesListCode = if (cond.values.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(" + cond.values.joinToString { "\"$it\"" } + ")"
            }
            builder.add(
                "%T(source = %T.%L, target = %S, operator = %T.%L, value = %S, values = %L)",
                cnCondition,
                cnSource,
                cond.source.name,
                cond.target,
                cnOperator,
                cond.operator.name,
                cond.value,
                valuesListCode
            )
            if (index < group.conditions.size - 1) builder.add(",\n") else builder.add("\n")
        }
        builder.unindent()
        builder.add("), isOr = %L)", group.isOr)
        return builder.build()
    }

    fun validateCapabilityConditions(
        capabilityName: String,
        parameters: Map<String, org.wip.plugintoolkit.api.ConditionGroup?>,
        availableSettings: Set<String>,
        logger: com.google.devtools.ksp.processing.KSPLogger,
        originNode: com.google.devtools.ksp.symbol.KSNode
    ) {
        val adj = mutableMapOf<String, MutableList<String>>()
        parameters.keys.forEach { adj[it] = mutableListOf() }

        parameters.forEach { (paramName, conditionGroup) ->
            conditionGroup?.conditions?.forEach { cond ->
                when (cond.source) {
                    org.wip.plugintoolkit.api.ConditionSource.PARAMETER -> {
                        if (!parameters.containsKey(cond.target)) {
                            logger.error(
                                "Parameter '$paramName' in capability '$capabilityName' depends on unknown parameter '${cond.target}'",
                                originNode
                            )
                        } else if (cond.target == paramName) {
                            logger.error(
                                "Parameter '$paramName' in capability '$capabilityName' cannot depend on itself",
                                originNode
                            )
                        } else {
                            adj[paramName]?.add(cond.target)
                        }
                    }

                    org.wip.plugintoolkit.api.ConditionSource.SETTING -> {
                        if (availableSettings.isNotEmpty() && !availableSettings.contains(cond.target)) {
                            logger.error(
                                "Parameter '$paramName' in capability '$capabilityName' depends on unknown setting '${cond.target}'",
                                originNode
                            )
                        }
                    }

                    org.wip.plugintoolkit.api.ConditionSource.LOCK -> {
                        // Dynamic runtime check
                    }
                }
            }
        }

        // Cycle detection via DFS
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(current: String): Boolean {
            visited.add(current)
            inStack.add(current)
            path.add(current)

            for (neighbor in adj[current] ?: emptyList()) {
                if (neighbor in inStack) {
                    val cycleStartIndex = path.indexOf(neighbor)
                    val cyclePath = path.subList(cycleStartIndex, path.size) + neighbor
                    logger.error(
                        "Circular parameter dependency detected in capability '$capabilityName': ${cyclePath.joinToString(" -> ")}",
                        originNode
                    )
                    return true
                }
                if (neighbor !in visited) {
                    if (dfs(neighbor)) return true
                }
            }

            path.removeAt(path.size - 1)
            inStack.remove(current)
            return false
        }

        for (param in parameters.keys) {
            if (param !in visited) {
                dfs(param)
            }
        }
    }
}
