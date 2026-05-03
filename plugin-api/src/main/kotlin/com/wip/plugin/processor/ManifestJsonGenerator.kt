package com.wip.plugin.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.json.Json
import com.wip.plugin.api.*

object ManifestJsonGenerator {
    fun generate(
        classDeclaration: KSClassDeclaration,
        id: String,
        name: String,
        version: String,
        description: String,
        minMemoryMb: Int,
        minExecutionTimeMs: Int,
        functions: List<KSFunctionDeclaration>,
        settingsProperties: List<KSPropertyDeclaration>,
        changelogObj: Changelog?
    ): String {
        val manifestCapabilities = functions.map { func ->
            val capAnn = func.annotations.first { it.shortName.asString() == "Capability" }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val capDesc = capAnn.arguments.find { it.name?.asString() == "description" }?.value as String
            val supportsPause = capAnn.arguments.find { it.name?.asString() == "supportsPause" }?.value as? Boolean ?: false
            val supportsCancel = capAnn.arguments.find { it.name?.asString() == "supportsCancel" }?.value as? Boolean ?: true
            
            val params = func.parameters.filter { param ->
                val paramType = param.type.resolve().toTypeName().toString()
                paramType != "com.wip.plugin.api.PluginLogger" && 
                paramType != "com.wip.plugin.api.ProgressReporter" && 
                paramType != "com.wip.plugin.api.PluginFileSystem" && 
                paramType != "com.wip.plugin.api.ExecutionContext"
            }.associate { param ->
                val paramAnn = param.annotations.find { it.shortName.asString() == "CapabilityParam" }
                val paramDesc = paramAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                val defaultValue = paramAnn?.arguments?.find { it.name?.asString() == "defaultValue" }?.value as? String ?: ""
                val paramName = param.name?.asString() ?: ""
                val ksType = param.type.resolve()
                
                val defaultJson = if (defaultValue.isNotEmpty()) {
                    try { Json.parseToJsonElement(defaultValue) } catch(e: Exception) { null }
                } else null
                
                val minValue = paramAnn?.arguments?.find { it.name?.asString() == "minValue" }?.value as? Double ?: Double.NaN
                val maxValue = paramAnn?.arguments?.find { it.name?.asString() == "maxValue" }?.value as? Double ?: Double.NaN
                val minLength = paramAnn?.arguments?.find { it.name?.asString() == "minLength" }?.value as? Int ?: -1
                val maxLength = paramAnn?.arguments?.find { it.name?.asString() == "maxLength" }?.value as? Int ?: -1
                val regex = paramAnn?.arguments?.find { it.name?.asString() == "regex" }?.value as? String ?: ""
                val multiSelect = paramAnn?.arguments?.find { it.name?.asString() == "multiSelect" }?.value as? Boolean ?: false
                val minChoices = paramAnn?.arguments?.find { it.name?.asString() == "minChoices" }?.value as? Int ?: -1
                val maxChoices = paramAnn?.arguments?.find { it.name?.asString() == "maxChoices" }?.value as? Int ?: -1
                
                val hasConstraints = !minValue.isNaN() || !maxValue.isNaN() || minLength != -1 || maxLength != -1 || regex.isNotEmpty() || multiSelect || minChoices != -1 || maxChoices != -1
                
                val constraints = if (hasConstraints) {
                    ParameterConstraints(
                        minValue = if (!minValue.isNaN()) minValue else null,
                        maxValue = if (!maxValue.isNaN()) maxValue else null,
                        minLength = if (minLength != -1) minLength else null,
                        maxLength = if (maxLength != -1) maxLength else null,
                        regex = if (regex.isNotEmpty()) regex else null,
                        multiSelect = if (multiSelect) true else null,
                        minChoices = if (minChoices != -1) minChoices else null,
                        maxChoices = if (maxChoices != -1) maxChoices else null
                    )
                } else null
                
                paramName to ParameterMetadata(
                    defaultValue = defaultJson,
                    description = paramDesc,
                    type = GeneratorUtils.mapKSTypeToDataType(ksType),
                    constraints = constraints
                )
            }
            
            val hasResumeState = func.parameters.any { param -> 
                param.annotations.any { it.shortName.asString() == "ResumeState" }
            }
            
            Capability(
                name = capName,
                description = capDesc,
                parameters = if (params.isEmpty()) null else params,
                returnType = GeneratorUtils.mapKSTypeToDataType(func.returnType!!.resolve()),
                isPausable = supportsPause || hasResumeState,
                isCancellable = supportsCancel
            )
        }

        val manifestSettings = settingsProperties.associate { prop ->
            val ann = prop.annotations.first { it.shortName.asString() == "PluginSetting" }
            val desc = ann.arguments.find { it.name?.asString() == "description" }?.value as String
            val defaultVal = ann.arguments.find { it.name?.asString() == "defaultValue" }?.value as String
            val propName = prop.simpleName.asString()
            val ksType = prop.type.resolve()
            
            val defaultJson = if (defaultVal.isNotEmpty()) {
                try { Json.parseToJsonElement(defaultVal) } catch(e: Exception) { null }
            } else null
            
            propName to SettingMetadata(
                defaultValue = defaultJson,
                description = desc,
                type = GeneratorUtils.mapKSTypeToDataType(ksType)
            )
        }

        val manifestObj = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(id = id, name = name, version = version, description = description),
            requirements = Requirements(minMemoryMb = minMemoryMb, minExecutionTimeMs = minExecutionTimeMs),
            capabilities = manifestCapabilities,
            settings = if (manifestSettings.isEmpty()) null else manifestSettings,
            changelog = changelogObj
        )
        
        val json = Json { prettyPrint = true }
        return json.encodeToString(manifestObj)
    }
}
