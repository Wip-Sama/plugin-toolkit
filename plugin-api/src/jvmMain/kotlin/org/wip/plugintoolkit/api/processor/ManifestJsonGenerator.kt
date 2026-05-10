package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.*
import org.wip.plugintoolkit.api.Changelog
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName

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
        actions: List<KSFunctionDeclaration>,
        changelogObj: Changelog?
    ): String {
        val manifestCapabilities = functions.map { func ->
            val capAnn = func.annotations.first { it.hasQualifiedName(CAPABILITY_ANNOTATION) }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val capDesc = capAnn.arguments.find { it.name?.asString() == "description" }?.value as String
            val supportsPause = capAnn.arguments.find { it.name?.asString() == "supportsPause" }?.value as? Boolean ?: false
            val supportsCancel = capAnn.arguments.find { it.name?.asString() == "supportsCancel" }?.value as? Boolean ?: true
            
            val params = func.parameters.filter { param ->
                val paramType = param.type.resolve().toTypeName().toString()
                paramType != PluginLogger::class.qualifiedName && 
                paramType != ProgressReporter::class.qualifiedName && 
                paramType != PluginFileSystem::class.qualifiedName && 
                paramType != PluginContext::class.qualifiedName
            }.associate { param ->
                val paramAnn = param.annotations.find { it.hasQualifiedName(CAPABILITY_PARAM_ANNOTATION) }
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
                val required = paramAnn?.arguments?.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
                val secret = paramAnn?.arguments?.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
                
                val hasConstraints = !minValue.isNaN() || !maxValue.isNaN() || minLength != -1 || maxLength != -1 || regex.isNotEmpty() || multiSelect || minChoices != -1 || maxChoices != -1
                
                val constraints = if (hasConstraints) {
                    ParameterConstraints(
                        minValue = if (!minValue.isNaN()) minValue else null,
                        maxValue = if (!maxValue.isNaN()) maxValue else null,
                        minLength = if (minLength != -1) minLength else null,
                        maxLength = if (maxLength != -1) maxLength else null,
                        regex = regex.ifEmpty { null },
                        multiSelect = if (multiSelect) true else null,
                        minChoices = if (minChoices != -1) minChoices else null,
                        maxChoices = if (maxChoices != -1) maxChoices else null
                    )
                } else null
                
                paramName to ParameterMetadata(
                    defaultValue = defaultJson,
                    description = paramDesc,
                    type = GeneratorUtils.mapKSTypeToDataType(ksType),
                    constraints = constraints,
                    required = required,
                    secret = secret
                )
            }
            
            val hasResumeState = func.parameters.any { param -> 
                param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) }
            }

            Capability(
                name = capName,
                description = capDesc,
                parameters = params.ifEmpty { null },
                returnType = GeneratorUtils.mapKSTypeToDataType(func.returnType!!.resolve()),
                isPausable = supportsPause || hasResumeState,
                isCancellable = supportsCancel
            )
        }

        val manifestSettings = settingsProperties.associate { prop ->
            val ann = prop.annotations.first { it.hasQualifiedName(PLUGIN_SETTING_ANNOTATION) }
            val desc = ann.arguments.find { it.name?.asString() == "description" }?.value as String
            val defaultVal = ann.arguments.find { it.name?.asString() == "defaultValue" }?.value as String
            val propName = prop.simpleName.asString()
            val ksType = prop.type.resolve()
            val required = ann.arguments.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
            val secret = ann.arguments.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
            
            val defaultJson = if (defaultVal.isNotEmpty()) {
                try { Json.parseToJsonElement(defaultVal) } catch(e: Exception) { null }
            } else null
            
            propName to SettingMetadata(
                defaultValue = defaultJson,
                description = desc,
                type = GeneratorUtils.mapKSTypeToDataType(ksType),
                required = required,
                secret = secret
            )
        }

        val manifestActions = actions.map { func ->
            val ann = func.annotations.first { it.hasQualifiedName(PLUGIN_ACTION_ANNOTATION) }
            val actName = ann.arguments.find { it.name?.asString() == "name" }?.value as String
            val actDesc = ann.arguments.find { it.name?.asString() == "description" }?.value as String
            
            PluginAction(
                name = actName,
                description = actDesc,
                functionName = func.simpleName.asString()
            )
        }

        val setupFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_SETUP_ANNOTATION) } }
        val updateFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_UPDATE_ANNOTATION) } }

        val manifestObj = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(
                id = id,
                name = name,
                version = version,
                description = description
            ),
            requirements = Requirements(
                minMemoryMb = minMemoryMb,
                minExecutionTimeMs = minExecutionTimeMs
            ),
            capabilities = manifestCapabilities,
            actions = manifestActions,
            settings = manifestSettings.ifEmpty { null },
            changelog = changelogObj,
            hasUpdateHandler = updateFunction != null,
            hasSetupHandler = setupFunction != null
        )
        
        val json = Json { prettyPrint = true }
        return json.encodeToString(manifestObj)
    }
}
