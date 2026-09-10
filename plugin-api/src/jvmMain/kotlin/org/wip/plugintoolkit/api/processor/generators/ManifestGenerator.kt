package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.GeneratorUtils
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_CAPABILITY
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_JSON
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PARAMETER_CONSTRAINTS
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PARAMETER_METADATA
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_ACTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_INFO
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_MANIFEST
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_REQUIREMENTS
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_SETTING_METADATA
import org.wip.plugintoolkit.api.processor.ProcessorConstants.INFRASTRUCTURE_TYPES
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_GET_DATA_TYPE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_ACTION_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETTING_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.RESUME_STATE_ANNOTATION

object ManifestGenerator {
    private fun generateSemanticTypesCode(types: List<org.wip.plugintoolkit.api.SemanticType>): CodeBlock {
        if (types.isEmpty()) return CodeBlock.of("emptyList()")
        val builder = CodeBlock.builder()
        builder.add("listOf(\n")
        builder.indent()
        types.forEachIndexed { idx, type ->
            val ns = if (type.namespace != null) "\"${type.namespace}\"" else "null"
            val variant = if (type.variant != null) "\"${type.variant}\"" else "null"
            builder.add(
                "%T(%L, %S, %L)",
                ClassName("org.wip.plugintoolkit.api", "SemanticType"),
                ns,
                type.name,
                variant
            )
            if (idx < types.size - 1) builder.add(",\n") else builder.add("\n")
        }
        builder.unindent()
        builder.add(")")
        return builder.build()
    }

    fun generateManifestObject(
        manifestName: String,
        id: String,
        name: String,
        version: String,
        description: String,
        minMemoryMb: Int,
        minExecutionTimeMs: Int,
        supportedOs: List<org.wip.plugintoolkit.api.OS>,
        functions: List<KSFunctionDeclaration>,
        settingsProperties: List<KSPropertyDeclaration>,
        actions: List<KSFunctionDeclaration>,
        hasUpdateHandler: Boolean,
        hasSetupHandler: Boolean
    ): TypeSpec {
        val manifestType = TypeSpec.objectBuilder(manifestName)

        val capabilitiesCode = CodeBlock.builder()
        capabilitiesCode.add("listOf(\n")
        capabilitiesCode.indent()
        functions.forEachIndexed { index, func ->
            val capAnn = func.annotations.first { it.hasQualifiedName(CAPABILITY_ANNOTATION) }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val capDesc = capAnn.arguments.find { it.name?.asString() == "description" }?.value as String
            val supportsPause =
                capAnn.arguments.find { it.name?.asString() == "supportsPause" }?.value as? Boolean ?: false
            val supportsCancel =
                capAnn.arguments.find { it.name?.asString() == "supportsCancel" }?.value as? Boolean ?: true

            val contextArg = capAnn.arguments.find { it.name?.asString() == "context" }?.value
            val contextName = when (contextArg) {
                is com.google.devtools.ksp.symbol.KSClassDeclaration -> contextArg.simpleName.asString()
                is com.google.devtools.ksp.symbol.KSType -> contextArg.declaration.simpleName.asString()
                else -> contextArg?.toString()?.substringAfterLast('.')?.substringAfterLast(':')?.trim() ?: "ANY"
            }
            val requiresSettingsList =
                (capAnn.arguments.find { it.name?.asString() == "requiresSettings" }?.value as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()

            val hasResumeState = func.parameters.any { param ->
                param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) }
            }

            var inferredReadsFiles = false
            var inferredWritesFiles = false
            var inferredDestructive = false

            func.parameters.forEach { param ->
                val isInputLoc =
                    param.annotations.any { it.hasQualifiedName(ProcessorConstants.CAPABILITY_INPUT_ANNOTATION) }
                val isOutputLoc =
                    param.annotations.any { it.hasQualifiedName(ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION) }
                val outputAnn =
                    param.annotations.find { it.hasQualifiedName(ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION) }
                val paramAnnLocal =
                    param.annotations.find { 
                        it.hasQualifiedName(ProcessorConstants.CAPABILITY_PARAM_ANNOTATION) ||
                        it.hasQualifiedName(ProcessorConstants.CAPABILITY_INPUT_ANNOTATION) ||
                        it.hasQualifiedName(ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION)
                    }
                val semTypesVal =
                    (paramAnnLocal?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                        ?: emptyList()
                val semanticTypesList = semTypesVal.flatMap { org.wip.plugintoolkit.api.parseSemanticTypes(it) }

                if (isInputLoc) inferredReadsFiles = true
                if (isOutputLoc) {
                    inferredWritesFiles = true
                    val destr = outputAnn?.arguments?.find { it.name?.asString() == "isDestructive" }?.value as? Boolean
                        ?: false
                    if (destr) inferredDestructive = true
                }
                semanticTypesList.forEach { st ->
                    val fullType = "${st.namespace}/${st.name}"
                    if (fullType == "path/file" || fullType == "path/folder") {
                        inferredReadsFiles = true
                    }
                }
            }

            capabilitiesCode.add("%T(\n", CN_CAPABILITY)
            capabilitiesCode.indent()
            capabilitiesCode.add("name = %S,\n", capName)
            capabilitiesCode.add("description = %S,\n", capDesc)
            capabilitiesCode.add("isPausable = %L,\n", supportsPause || hasResumeState)
            capabilitiesCode.add("isCancellable = %L,\n", supportsCancel)
            capabilitiesCode.add(
                "context = %T.%L,\n",
                ClassName("org.wip.plugintoolkit.api", "CapabilityContext"),
                contextName
            )

            val fileAccessCode = if (inferredReadsFiles || inferredWritesFiles || inferredDestructive) {
                CodeBlock.of(
                    "%T(readsFiles = %L, writesFiles = %L, isDestructive = %L)",
                    ClassName("org.wip.plugintoolkit.api", "FileAccess"),
                    inferredReadsFiles,
                    inferredWritesFiles,
                    inferredDestructive
                )
            } else {
                CodeBlock.of("null")
            }
            val reqLockAnn = func.annotations.find { it.hasQualifiedName(ProcessorConstants.REQUIRES_LOCK_ANNOTATION) }
            val requiredLocksList =
                (reqLockAnn?.arguments?.find { it.name?.asString() == "locks" }?.value as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()

            capabilitiesCode.add("fileAccess = %L,\n", fileAccessCode)
            if (requiresSettingsList.isEmpty()) {
                capabilitiesCode.add("requiresSettings = emptyList(),\n")
            } else {
                capabilitiesCode.add(
                    "requiresSettings = listOf(%L),\n",
                    requiresSettingsList.joinToString { "\"$it\"" })
            }
            if (requiredLocksList.isEmpty()) {
                capabilitiesCode.add("requiredLocks = emptyList(),\n")
            } else {
                capabilitiesCode.add(
                    "requiredLocks = listOf(%L),\n",
                    requiredLocksList.joinToString { "\"$it\"" })
            }
            capabilitiesCode.add("parameters = mapOf(\n")
            capabilitiesCode.indent()

            val unpackedParams = GeneratorUtils.getCapabilityParameters(func)
            unpackedParams.forEachIndexed { pIndex, param ->
                val defaultValueCode = if (param.defaultValue.isNotEmpty()) {
                    try {
                        kotlinx.serialization.json.Json.parseToJsonElement(param.defaultValue)
                        CodeBlock.of("%T.parseToJsonElement(%S)", CN_JSON, param.defaultValue)
                    } catch (e: Exception) {
                        CodeBlock.of("%T(%S)", ClassName("kotlinx.serialization.json", "JsonPrimitive"), param.defaultValue)
                    }
                } else {
                    CodeBlock.of("%L", "null")
                }

                val constraintsCode = if (param.constraints != null) {
                    val c = param.constraints
                    val regexCode = if (!c.regex.isNullOrEmpty()) CodeBlock.of("%S", c.regex) else CodeBlock.of("null")
                    CodeBlock.of(
                        "%T(minValue = %L, maxValue = %L, minLength = %L, maxLength = %L, regex = %L, multiSelect = %L, minChoices = %L, maxChoices = %L)",
                        CN_PARAMETER_CONSTRAINTS,
                        if (c.minValue != null) c.minValue else "null",
                        if (c.maxValue != null) c.maxValue else "null",
                        if (c.minLength != null) c.minLength else "null",
                        if (c.maxLength != null) c.maxLength else "null",
                        regexCode,
                        if (c.multiSelect == true) "true" else "null",
                        if (c.minChoices != null) c.minChoices else "null",
                        if (c.maxChoices != null) c.maxChoices else "null"
                    )
                } else "null"

                val roleStr = when (param.role) {
                    org.wip.plugintoolkit.api.ParameterRole.INPUT_LOCATION -> "INPUT_LOCATION"
                    org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION -> "OUTPUT_LOCATION"
                    else -> "STANDARD"
                }
                val roleCode = CodeBlock.of("%T.%L", org.wip.plugintoolkit.api.ParameterRole::class, roleStr)

                val autogeneratedPatternCode = if (param.autogeneratedPattern != null) CodeBlock.of(
                    "%S",
                    param.autogeneratedPattern
                ) else CodeBlock.of("null")

                val semanticTypesCode = generateSemanticTypesCode(param.semanticTypes)
                val typeCode = GeneratorUtils.generateDataTypeCode(param.dataType)
                val conditionCode = org.wip.plugintoolkit.api.processor.GeneratorUtils.generateConditionGroupCode(param.condition)

                capabilitiesCode.add(
                    "%S to %T(defaultValue = %L, description = %S, type = %L, constraints = %L, required = %L, secret = %L, semanticTypes = %L, role = %L, autogeneratedPattern = %L, isDestructive = %L, isAdvanced = %L, condition = %L)",
                    param.name,
                    CN_PARAMETER_METADATA,
                    defaultValueCode,
                    param.description,
                    typeCode,
                    constraintsCode,
                    param.required,
                    param.secret,
                    semanticTypesCode,
                    roleCode,
                    autogeneratedPatternCode,
                    param.isDestructive,
                    param.isAdvanced,
                    conditionCode
                )
                if (pIndex < unpackedParams.size - 1) capabilitiesCode.add(",\n") else capabilitiesCode.add("\n")
            }
            capabilitiesCode.unindent()
            capabilitiesCode.add("),\n")

            val returnTypeKS = func.returnType?.resolve()
            val returnDataType =
                if (returnTypeKS != null) org.wip.plugintoolkit.api.processor.GeneratorUtils.mapKSTypeToDataType(
                    returnTypeKS
                ) else org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.UNIT)
            val returnDataTypeCode =
                org.wip.plugintoolkit.api.processor.GeneratorUtils.generateDataTypeCode(returnDataType)

            val outputs = org.wip.plugintoolkit.api.processor.GeneratorUtils.getCapabilityOutputs(func)
            val outputsCode = CodeBlock.builder()
            outputsCode.add("listOf(\n")
            outputsCode.indent()
            outputs.forEachIndexed { oIndex, out ->
                val semanticTypesCode = generateSemanticTypesCode(out.semanticTypes)
                val outConditionCode = org.wip.plugintoolkit.api.processor.GeneratorUtils.generateConditionGroupCode(out.condition)
                outputsCode.add(
                    "%T(name = %S, description = %S, type = %L, semanticTypes = %L, isAdvanced = %L, condition = %L)",
                    ClassName("org.wip.plugintoolkit.api", "OutputMetadata"),
                    out.name,
                    out.description,
                    org.wip.plugintoolkit.api.processor.GeneratorUtils.generateDataTypeCode(out.type),
                    semanticTypesCode,
                    out.isAdvanced,
                    outConditionCode
                )
                if (oIndex < outputs.size - 1) outputsCode.add(",\n") else outputsCode.add("\n")
            }
            outputsCode.unindent()
            outputsCode.add(")")

            val semanticTypesList = if (outputs.size == 1) outputs.first().semanticTypes else emptyList()
            val semanticTypesCode = generateSemanticTypesCode(semanticTypesList)

            capabilitiesCode.add("returnType = %L,\n", returnDataTypeCode)
            capabilitiesCode.add("semanticTypes = %L,\n", semanticTypesCode)
            capabilitiesCode.add("outputs = %L\n", outputsCode.build())
            capabilitiesCode.unindent()
            if (index < functions.size - 1) capabilitiesCode.add("),\n") else capabilitiesCode.add(")\n")
        }
        capabilitiesCode.unindent()
        capabilitiesCode.add(")\n")

        val settingsCode = CodeBlock.builder()
        settingsCode.add("mapOf(\n")
        settingsCode.indent()
        settingsProperties.forEachIndexed { index, prop ->
            val ann = prop.annotations.first { it.hasQualifiedName(PLUGIN_SETTING_ANNOTATION) }
            val desc = ann.arguments.find { it.name?.asString() == "description" }?.value as String
            val defaultVal = ann.arguments.find { it.name?.asString() == "defaultValue" }?.value as String
            val explicitRequired = ann.arguments.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
            val isNullable = prop.type.resolve().isMarkedNullable
            val required = explicitRequired || !isNullable
            val secret = ann.arguments.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
            val propName = prop.simpleName.asString()
            val propType = prop.type.resolve().toTypeName()
            val defaultValueCode = if (defaultVal.isNotEmpty()) {
                try {
                    kotlinx.serialization.json.Json.parseToJsonElement(defaultVal)
                    CodeBlock.of("%T.parseToJsonElement(%S)", CN_JSON, defaultVal)
                } catch (e: Exception) {
                    CodeBlock.of("%T(%S)", ClassName("kotlinx.serialization.json", "JsonPrimitive"), defaultVal)
                }
            } else {
                CodeBlock.of("null")
            }


            settingsCode.add(
                "%S to %T(defaultValue = %L, description = %S, type = %M<%T>(), required = %L, secret = %L)",
                propName,
                CN_SETTING_METADATA,
                defaultValueCode,
                desc,
                MN_GET_DATA_TYPE,
                propType,
                required,
                secret
            )
            if (index < settingsProperties.size - 1) settingsCode.add(",\n") else settingsCode.add("\n")
        }
        settingsCode.unindent()
        settingsCode.add(")\n")

        val actionsCode = CodeBlock.builder()
        actionsCode.add("listOf(\n")
        actionsCode.indent()
        actions.forEachIndexed { index, func ->
            val ann = func.annotations.first { it.hasQualifiedName(PLUGIN_ACTION_ANNOTATION) }
            val actName = ann.arguments.find { it.name?.asString() == "name" }?.value as String
            val actDesc = ann.arguments.find { it.name?.asString() == "description" }?.value as String

            val nonInfraParams = func.parameters.filter { param ->
                val paramType = param.type.resolve().toTypeName()
                INFRASTRUCTURE_TYPES.none { it == paramType }
            }

            if (nonInfraParams.isEmpty()) {
                actionsCode.add(
                    "%T(name = %S, description = %S, functionName = %S)",
                    CN_PLUGIN_ACTION,
                    actName,
                    actDesc,
                    func.simpleName.asString()
                )
            } else {
                val actionParamsCode = CodeBlock.builder()
                actionParamsCode.add("mapOf(\n")
                actionParamsCode.indent()
                nonInfraParams.forEachIndexed { pIndex, param ->
                    val paramNameStr = param.name?.asString() ?: ""
                    val paramType = param.type.resolve().toTypeName()
                    val isNullable = param.type.resolve().isMarkedNullable
                    val hasDefault = param.hasDefault
                    val required = !isNullable && !hasDefault

                    actionParamsCode.add(
                        "%S to %T(description = %S, type = %M<%T>(), required = %L)",
                        paramNameStr,
                        CN_PARAMETER_METADATA,
                        "",
                        MN_GET_DATA_TYPE,
                        paramType,
                        required
                    )
                    if (pIndex < nonInfraParams.size - 1) actionParamsCode.add(",\n") else actionParamsCode.add("\n")
                }
                actionParamsCode.unindent()
                actionParamsCode.add(")")

                actionsCode.add(
                    "%T(name = %S, description = %S, functionName = %S, parameters = %L)",
                    CN_PLUGIN_ACTION,
                    actName,
                    actDesc,
                    func.simpleName.asString(),
                    actionParamsCode.build()
                )
            }
            if (index < actions.size - 1) actionsCode.add(",\n") else actionsCode.add("\n")
        }
        actionsCode.unindent()
        actionsCode.add(")\n")

        val supportedOsCode = CodeBlock.builder()
        supportedOsCode.add("listOf(")
        supportedOs.forEachIndexed { sIndex, os ->
            supportedOsCode.add("%T.%L", ClassName("org.wip.plugintoolkit.api", "OS"), os.name)
            if (sIndex < supportedOs.size - 1) supportedOsCode.add(", ")
        }
        supportedOsCode.add(")")

        manifestType.addProperty(
            PropertySpec.builder("manifest", CN_PLUGIN_MANIFEST)
                .initializer(
                    CodeBlock.builder()
                        .add("%T(\n", CN_PLUGIN_MANIFEST)
                        .indent()
                        .add("manifestVersion = %S,\n", "1.0")
                        .add(
                            "plugin = %T(id = %S, name = %S, version = %S, description = %S, supportedOs = %L),\n",
                            CN_PLUGIN_INFO,
                            id,
                            name,
                            version,
                            description,
                            supportedOsCode.build()
                        )
                        .add(
                            "requirements = %T(minMemoryMb = %L, minExecutionTimeMs = %L),\n",
                            CN_REQUIREMENTS,
                            minMemoryMb,
                            minExecutionTimeMs
                        )
                        .add("capabilities = ")
                        .add(capabilitiesCode.build())
                        .add(",\nactions = ")
                        .add(actionsCode.build())
                        .add(",\nsettings = ")
                        .add(settingsCode.build())
                        .add(",\nhasUpdateHandler = %L,\nhasSetupHandler = %L\n", hasUpdateHandler, hasSetupHandler)
                        .unindent()
                        .add(")")
                        .build()
                )
                .build()
        )
        return manifestType.build()
    }
}
