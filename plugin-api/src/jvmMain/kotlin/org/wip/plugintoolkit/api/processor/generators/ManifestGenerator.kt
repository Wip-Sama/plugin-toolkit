package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_PARAM_ANNOTATION
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

            val contextEnumKS =
                capAnn.arguments.find { it.name?.asString() == "context" }?.value as? com.google.devtools.ksp.symbol.KSType
            val contextName = contextEnumKS?.declaration?.simpleName?.asString() ?: "ANY"
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
                    param.annotations.any { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_INPUT_ANNOTATION) }
                val isOutputLoc =
                    param.annotations.any { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION) }
                val outputAnn =
                    param.annotations.find { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION) }
                val paramAnnLocal =
                    param.annotations.find { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_PARAM_ANNOTATION) }
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
            capabilitiesCode.add("fileAccess = %L,\n", fileAccessCode)
            if (requiresSettingsList.isEmpty()) {
                capabilitiesCode.add("requiresSettings = emptyList(),\n")
            } else {
                capabilitiesCode.add(
                    "requiresSettings = listOf(%L),\n",
                    requiresSettingsList.joinToString { "\"$it\"" })
            }
            capabilitiesCode.add("parameters = mapOf(\n")
            capabilitiesCode.indent()

            val paramsList = func.parameters
            paramsList.forEachIndexed { pIndex, param ->
                val paramAnn = param.annotations.find { it.hasQualifiedName(CAPABILITY_PARAM_ANNOTATION) }
                val paramDesc =
                    paramAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                val defaultValue =
                    paramAnn?.arguments?.find { it.name?.asString() == "defaultValue" }?.value as? String ?: ""
                val paramNameStr = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()

                val isInfrastructure = INFRASTRUCTURE_TYPES.any { it == paramType }

                if (!isInfrastructure) {
                    val defaultValueCode = if (defaultValue.isNotEmpty()) {
                        CodeBlock.of("%T.parseToJsonElement(%S)", CN_JSON, defaultValue)
                    } else {
                        CodeBlock.of("%L", "null")
                    }

                    val minValue =
                        paramAnn?.arguments?.find { it.name?.asString() == "minValue" }?.value as? Double ?: Double.NaN
                    val maxValue =
                        paramAnn?.arguments?.find { it.name?.asString() == "maxValue" }?.value as? Double ?: Double.NaN
                    val minLength =
                        paramAnn?.arguments?.find { it.name?.asString() == "minLength" }?.value as? Int ?: -1
                    val maxLength =
                        paramAnn?.arguments?.find { it.name?.asString() == "maxLength" }?.value as? Int ?: -1
                    val regex = paramAnn?.arguments?.find { it.name?.asString() == "regex" }?.value as? String ?: ""
                    val multiSelect =
                        paramAnn?.arguments?.find { it.name?.asString() == "multiSelect" }?.value as? Boolean ?: false
                    val minChoices =
                        paramAnn?.arguments?.find { it.name?.asString() == "minChoices" }?.value as? Int ?: -1
                    val maxChoices =
                        paramAnn?.arguments?.find { it.name?.asString() == "maxChoices" }?.value as? Int ?: -1
                    val isNullable = param.type.resolve().isMarkedNullable
                    val hasDefault = param.hasDefault
                    val explicitRequired =
                        paramAnn?.arguments?.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
                    val required = explicitRequired || (!isNullable && !hasDefault)
                    val secret =
                        paramAnn?.arguments?.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
                    val semTypesVal =
                        (paramAnn?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                            ?: emptyList()

                    val hasConstraints =
                        !minValue.isNaN() || !maxValue.isNaN() || minLength != -1 || maxLength != -1 || regex.isNotEmpty() || multiSelect || minChoices != -1 || maxChoices != -1

                    val constraintsCode = if (hasConstraints) {
                        val regexCode = if (regex.isNotEmpty()) CodeBlock.of("%S", regex) else CodeBlock.of("null")
                        CodeBlock.of(
                            "%T(minValue = %L, maxValue = %L, minLength = %L, maxLength = %L, regex = %L, multiSelect = %L, minChoices = %L, maxChoices = %L)",
                            CN_PARAMETER_CONSTRAINTS,
                            if (!minValue.isNaN()) minValue else "null",
                            if (!maxValue.isNaN()) maxValue else "null",
                            if (minLength != -1) minLength else "null",
                            if (maxLength != -1) maxLength else "null",
                            regexCode,
                            if (multiSelect) "true" else "null",
                            if (minChoices != -1) minChoices else "null",
                            if (maxChoices != -1) maxChoices else "null"
                        )
                    } else "null"

                    val isInputLoc =
                        param.annotations.any { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_INPUT_ANNOTATION) }
                    val isOutputLoc =
                        param.annotations.any { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION) }
                    val roleStr = when {
                        isInputLoc -> "INPUT_LOCATION"
                        isOutputLoc -> "OUTPUT_LOCATION"
                        else -> "STANDARD"
                    }
                    val roleCode = CodeBlock.of("%T.%L", org.wip.plugintoolkit.api.ParameterRole::class, roleStr)

                    val outputAnn =
                        param.annotations.find { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION) }
                    val autogeneratedPattern = if (isOutputLoc) {
                        outputAnn?.arguments?.find { it.name?.asString() == "autogeneratedPattern" }?.value as? String
                            ?: ""
                    } else null

                    val isDestructive = if (isOutputLoc) {
                        outputAnn?.arguments?.find { it.name?.asString() == "isDestructive" }?.value as? Boolean
                            ?: false
                    } else false

                    val semanticTypesList = semTypesVal.flatMap { org.wip.plugintoolkit.api.parseSemanticTypes(it) }
                    val semanticTypesCode = generateSemanticTypesCode(semanticTypesList)

                    val autogeneratedPatternCode = if (autogeneratedPattern != null) CodeBlock.of(
                        "%S",
                        autogeneratedPattern
                    ) else CodeBlock.of("null")

                    capabilitiesCode.add(
                        "%S to %T(defaultValue = %L, description = %S, type = %M<%T>(), constraints = %L, required = %L, secret = %L, semanticTypes = %L, role = %L, autogeneratedPattern = %L, isDestructive = %L)",
                        paramNameStr,
                        CN_PARAMETER_METADATA,
                        defaultValueCode,
                        paramDesc,
                        MN_GET_DATA_TYPE,
                        paramType,
                        constraintsCode,
                        required,
                        secret,
                        semanticTypesCode,
                        roleCode,
                        autogeneratedPatternCode,
                        isDestructive
                    )
                    if (pIndex < paramsList.size - 1) capabilitiesCode.add(",\n") else capabilitiesCode.add("\n")
                }
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
                outputsCode.add(
                    "%T(name = %S, description = %S, type = %L, semanticTypes = %L)",
                    ClassName("org.wip.plugintoolkit.api", "OutputMetadata"),
                    out.name,
                    out.description,
                    org.wip.plugintoolkit.api.processor.GeneratorUtils.generateDataTypeCode(out.type),
                    semanticTypesCode
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
                CodeBlock.of("%T.parseToJsonElement(%S)", CN_JSON, defaultVal)
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

            actionsCode.add(
                "%T(name = %S, description = %S, functionName = %S)",
                CN_PLUGIN_ACTION,
                actName,
                actDesc,
                func.simpleName.asString()
            )
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
