package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.Changelog
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.OutputMetadata
import org.wip.plugintoolkit.api.ParameterConstraints
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.api.SettingMetadata
import org.wip.plugintoolkit.api.parseSemanticTypes
import org.wip.plugintoolkit.api.FileAccess
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_PARAM_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_INPUT_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_OUTPUT_ANNOTATION
import org.wip.plugintoolkit.api.ParameterRole
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_ACTION_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETTING_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETUP_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_UPDATE_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.RESUME_STATE_ANNOTATION

object ManifestJsonGenerator {
    fun generate(
        classDeclaration: KSClassDeclaration,
        id: String,
        name: String,
        version: String,
        description: String,
        minMemoryMb: Int,
        minExecutionTimeMs: Int,
        supportedOs: List<OS>,
        functions: List<KSFunctionDeclaration>,
        settingsProperties: List<KSPropertyDeclaration>,
        actions: List<KSFunctionDeclaration>,
        changelogObj: Changelog?,
        hasMigrations: Boolean = false
    ): String {
        val manifestCapabilities = functions.map { func ->
            val capAnn = func.annotations.first { it.hasQualifiedName(CAPABILITY_ANNOTATION) }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val capDesc = capAnn.arguments.find { it.name?.asString() == "description" }?.value as String
            val supportsPause = capAnn.arguments.find { it.name?.asString() == "supportsPause" }?.value as? Boolean ?: false
            val supportsCancel = capAnn.arguments.find { it.name?.asString() == "supportsCancel" }?.value as? Boolean ?: true
            
            val contextEnumKS = capAnn.arguments.find { it.name?.asString() == "context" }?.value as? com.google.devtools.ksp.symbol.KSType
            val contextName = contextEnumKS?.declaration?.simpleName?.asString() ?: "ANY"
            val context = CapabilityContext.valueOf(contextName)
            val requiresSettingsList = (capAnn.arguments.find { it.name?.asString() == "requiresSettings" }?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            
            val params = func.parameters.filter { param ->
                val paramType = param.type.resolve().toTypeName().toString()
                paramType != PluginLogger::class.qualifiedName && 
                paramType != ProgressReporter::class.qualifiedName && 
                paramType != PluginFileSystem::class.qualifiedName && 
                paramType != HostFileSystem::class.qualifiedName && 
                paramType != PluginContext::class.qualifiedName
            }.associate { param ->
                val isInputLoc = param.annotations.any { it.hasQualifiedName(CAPABILITY_INPUT_ANNOTATION) }
                val isOutputLoc = param.annotations.any { it.hasQualifiedName(CAPABILITY_OUTPUT_ANNOTATION) }
                val paramAnn = param.annotations.find { 
                    it.hasQualifiedName(CAPABILITY_PARAM_ANNOTATION) ||
                    it.hasQualifiedName(CAPABILITY_INPUT_ANNOTATION) ||
                    it.hasQualifiedName(CAPABILITY_OUTPUT_ANNOTATION)
                }
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
                val isNullable = ksType.isMarkedNullable
                val hasDefault = param.hasDefault
                val explicitRequired = paramAnn?.arguments?.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
                val required = explicitRequired || (!isNullable && !hasDefault)
                val secret = paramAnn?.arguments?.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
                val semTypesVal = (paramAnn?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val pathTemplate = paramAnn?.arguments?.find { it.name?.asString() == "pathTemplate" }?.value as? String ?: ""
                
                val autogeneratedPattern = if (isOutputLoc) {
                    paramAnn?.arguments?.find { it.name?.asString() == "autogeneratedPattern" }?.value as? String ?: ""
                } else null
                
                val isDestructive = if (isOutputLoc) {
                    paramAnn?.arguments?.find { it.name?.asString() == "isDestructive" }?.value as? Boolean ?: false
                } else false
                
                val role = when {
                    isOutputLoc -> ParameterRole.OUTPUT_LOCATION
                    isInputLoc -> ParameterRole.INPUT_LOCATION
                    else -> ParameterRole.STANDARD
                }
                
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
                    secret = secret,
                    semanticTypes = semTypesVal.flatMap { parseSemanticTypes(it) },
                    role = role,
                    autogeneratedPattern = autogeneratedPattern,
                    isDestructive = isDestructive
                )
            }
            
            var inferredReadsFiles = false
            var inferredWritesFiles = false
            var inferredDestructive = false
            params.values.forEach { pMeta ->
                if (pMeta.role == ParameterRole.INPUT_LOCATION) {
                    inferredReadsFiles = true
                }
                if (pMeta.role == ParameterRole.OUTPUT_LOCATION) {
                    inferredWritesFiles = true
                    if (pMeta.isDestructive) inferredDestructive = true
                }
                
                // legacy support for semantic types
                pMeta.semanticTypes.forEach { st ->
                    val fullType = "${st.namespace}/${st.name}"
                    if (fullType == "file/path" || fullType == "folder/path") {
                        inferredReadsFiles = true
                    }
                    if (fullType == "file/output" || fullType == "folder/output") {
                        inferredWritesFiles = true
                    }
                }
            }

            val fileAccess = if (inferredReadsFiles || inferredWritesFiles || inferredDestructive) {
                FileAccess(
                    readsFiles = inferredReadsFiles,
                    writesFiles = inferredWritesFiles,
                    isDestructive = inferredDestructive
                )
            } else null
            
            val hasResumeState = func.parameters.any { param -> 
                param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) }
            }

            val outputs = GeneratorUtils.getCapabilityOutputs(func).map { out ->
                OutputMetadata(
                    name = out.name,
                    description = out.description,
                    type = out.type,
                    semanticTypes = out.semanticTypes
                )
            }

            Capability(
                name = capName,
                description = capDesc,
                parameters = params.ifEmpty { null },
                returnType = GeneratorUtils.mapKSTypeToDataType(func.returnType!!.resolve()),
                semanticTypes = if (outputs.size == 1) outputs.first().semanticTypes else emptyList(),
                outputs = outputs.ifEmpty { null },
                isPausable = supportsPause || hasResumeState,
                isCancellable = supportsCancel,
                context = context,
                requiresSettings = requiresSettingsList,
                fileAccess = fileAccess
            )
        }

        val manifestSettings = settingsProperties.associate { prop ->
            val ann = prop.annotations.first { it.hasQualifiedName(PLUGIN_SETTING_ANNOTATION) }
            val desc = ann.arguments.find { it.name?.asString() == "description" }?.value as String
            val defaultVal = ann.arguments.find { it.name?.asString() == "defaultValue" }?.value as String
            val propName = prop.simpleName.asString()
            val ksType = prop.type.resolve()
            val isNullable = ksType.isMarkedNullable
            val explicitRequired = ann.arguments.find { it.name?.asString() == "required" }?.value as? Boolean ?: false
            val required = explicitRequired || (!isNullable)
            val secret = ann.arguments.find { it.name?.asString() == "secret" }?.value as? Boolean ?: false
            
            val defaultJson = if (defaultVal.isNotEmpty()) {
                try { Json.parseToJsonElement(defaultVal) } catch(e: Exception) { null }
            } else null
            
            val minValue = ann.arguments.find { it.name?.asString() == "minValue" }?.value as? Double ?: Double.NaN
            val maxValue = ann.arguments.find { it.name?.asString() == "maxValue" }?.value as? Double ?: Double.NaN
            val minLength = ann.arguments.find { it.name?.asString() == "minLength" }?.value as? Int ?: -1
            val maxLength = ann.arguments.find { it.name?.asString() == "maxLength" }?.value as? Int ?: -1
            val regex = ann.arguments.find { it.name?.asString() == "regex" }?.value as? String ?: ""
            val multiSelect = ann.arguments.find { it.name?.asString() == "multiSelect" }?.value as? Boolean ?: false
            val minChoices = ann.arguments.find { it.name?.asString() == "minChoices" }?.value as? Int ?: -1
            val maxChoices = ann.arguments.find { it.name?.asString() == "maxChoices" }?.value as? Int ?: -1
            
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
            
            propName to SettingMetadata(
                defaultValue = defaultJson,
                description = desc,
                type = GeneratorUtils.mapKSTypeToDataType(ksType),
                required = required,
                secret = secret,
                constraints = constraints
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
                description = description,
                supportedOs = supportedOs
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
            hasSetupHandler = setupFunction != null,
            hasMigrations = hasMigrations
        )
        
        val json = Json { prettyPrint = true }
        return json.encodeToString(manifestObj)
    }
}
