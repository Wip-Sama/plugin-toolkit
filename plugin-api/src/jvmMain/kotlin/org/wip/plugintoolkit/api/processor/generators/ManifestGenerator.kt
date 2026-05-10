package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.CAPABILITY_PARAM_ANNOTATION
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.PLUGIN_ACTION_ANNOTATION
import org.wip.plugintoolkit.api.processor.PLUGIN_SETTING_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_CAPABILITY
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PARAMETER_CONSTRAINTS
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PARAMETER_METADATA
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_ACTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_INFO
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_MANIFEST
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_REQUIREMENTS
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_SETTING_METADATA
import org.wip.plugintoolkit.api.processor.ProcessorConstants.INFRASTRUCTURE_TYPES
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_GET_DATA_TYPE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_JSON
import org.wip.plugintoolkit.api.processor.RESUME_STATE_ANNOTATION

object ManifestGenerator {
    fun generateManifestObject(
        manifestName: String,
        id: String,
        name: String,
        version: String,
        description: String,
        minMemoryMb: Int,
        minExecutionTimeMs: Int,
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
            val supportsPause = capAnn.arguments.find { it.name?.asString() == "supportsPause" }?.value as? Boolean ?: false
            val supportsCancel = capAnn.arguments.find { it.name?.asString() == "supportsCancel" }?.value as? Boolean ?: true

            val hasResumeState = func.parameters.any { param -> 
                param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) }
            }

            capabilitiesCode.add("%T(\n", CN_CAPABILITY)
            capabilitiesCode.indent()
            capabilitiesCode.add("name = %S,\n", capName)
            capabilitiesCode.add("description = %S,\n", capDesc)
            capabilitiesCode.add("isPausable = %L,\n", supportsPause || hasResumeState)
            capabilitiesCode.add("isCancellable = %L,\n", supportsCancel)
            capabilitiesCode.add("parameters = mapOf(\n")
            capabilitiesCode.indent()
            
            val paramsList = func.parameters
            paramsList.forEachIndexed { pIndex, param ->
                val paramAnn = param.annotations.find { it.hasQualifiedName(CAPABILITY_PARAM_ANNOTATION) }
                val paramDesc = paramAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                val defaultValue = paramAnn?.arguments?.find { it.name?.asString() == "defaultValue" }?.value as? String ?: ""
                val paramNameStr = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                
                val isInfrastructure = INFRASTRUCTURE_TYPES.any { it == paramType }

                if (!isInfrastructure) {
                    val defaultValueCode = if (defaultValue.isNotEmpty()) {
                        CodeBlock.of("%T.parseToJsonElement(%S)", CN_JSON, defaultValue)
                    } else {
                        CodeBlock.of("%L", "null")
                    }
                    
                    val minValue = paramAnn?.arguments?.find { it.name?.asString() == "minValue" }?.value as? Double ?: Double.NaN
                    val maxValue = paramAnn?.arguments?.find { it.name?.asString() == "maxValue" }?.value as? Double ?: Double.NaN
                    val minLength = paramAnn?.arguments?.find { it.name?.asString() == "minLength" }?.value as? Int ?: -1
                    val maxLength = paramAnn?.arguments?.find { it.name?.asString() == "maxLength" }?.value as? Int ?: -1
                    val regex = paramAnn?.arguments?.find { it.name?.asString() == "regex" }?.value as? String ?: ""
                    val multiSelect = paramAnn?.arguments?.find { it.name?.asString() == "multiSelect" }?.value as? Boolean ?: false
                    val minChoices = paramAnn?.arguments?.find { it.name?.asString() == "minChoices" }?.value as? Int ?: -1
                    val maxChoices = paramAnn?.arguments?.find { it.name?.asString() == "maxChoices" }?.value as? Int ?: -1
                    val required = paramAnn?.arguments?.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
                    val secret = paramAnn?.arguments?.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
                    
                    val hasConstraints = !minValue.isNaN() || !maxValue.isNaN() || minLength != -1 || maxLength != -1 || regex.isNotEmpty() || multiSelect || minChoices != -1 || maxChoices != -1
                    
                    val constraintsCode = if (hasConstraints) {
                        CodeBlock.of("%T(minValue = ${if (!minValue.isNaN()) minValue else "null"}, maxValue = ${if (!maxValue.isNaN()) maxValue else "null"}, minLength = ${if (minLength != -1) minLength else "null"}, maxLength = ${if (maxLength != -1) maxLength else "null"}, regex = ${if (regex.isNotEmpty()) "\"$regex\"" else "null"}, multiSelect = ${if (multiSelect) "true" else "null"}, minChoices = ${if (minChoices != -1) minChoices else "null"}, maxChoices = ${if (maxChoices != -1) maxChoices else "null"})", CN_PARAMETER_CONSTRAINTS)
                    } else "null"
                    
                    capabilitiesCode.add("%S to %T(defaultValue = %L, description = %S, type = %M<%T>(), constraints = %L, required = %L, secret = %L)", paramNameStr, CN_PARAMETER_METADATA, defaultValueCode, paramDesc, MN_GET_DATA_TYPE, paramType, constraintsCode, required, secret)
                    if (pIndex < paramsList.size - 1) capabilitiesCode.add(",\n") else capabilitiesCode.add("\n")
                }
            }
            capabilitiesCode.unindent()
            capabilitiesCode.add("),\n")
            
            val returnType = func.returnType?.resolve()?.toTypeName() ?: UNIT
            capabilitiesCode.add("returnType = %M<%T>()\n", MN_GET_DATA_TYPE, returnType)
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
            val required = ann.arguments.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
            val secret = ann.arguments.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
            val propName = prop.simpleName.asString()
            val propType = prop.type.resolve().toTypeName()
            val defaultValueCode = if (defaultVal.isNotEmpty()) {
                CodeBlock.of("%T.parseToJsonElement(%S)", CN_JSON, defaultVal)
            } else {
                CodeBlock.of("null")
            }
            
            settingsCode.add("%S to %T(defaultValue = %L, description = %S, type = %M<%T>(), required = %L, secret = %L)", propName, CN_SETTING_METADATA, defaultValueCode, desc, MN_GET_DATA_TYPE, propType, required, secret)
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
            
            actionsCode.add("%T(name = %S, description = %S, functionName = %S)", CN_PLUGIN_ACTION, actName, actDesc, func.simpleName.asString())
            if (index < actions.size - 1) actionsCode.add(",\n") else actionsCode.add("\n")
        }
        actionsCode.unindent()
        actionsCode.add(")\n")

        manifestType.addProperty(
            PropertySpec.builder("manifest", CN_PLUGIN_MANIFEST)
                .initializer(
                    CodeBlock.builder()
                        .add("%T(\n", CN_PLUGIN_MANIFEST)
                        .indent()
                        .add("manifestVersion = %S,\n", "1.0")
                        .add("plugin = %T(id = %S, name = %S, version = %S, description = %S),\n", CN_PLUGIN_INFO, id, name, version, description)
                        .add("requirements = %T(minMemoryMb = %L, minExecutionTimeMs = %L),\n", CN_REQUIREMENTS, minMemoryMb, minExecutionTimeMs)
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
