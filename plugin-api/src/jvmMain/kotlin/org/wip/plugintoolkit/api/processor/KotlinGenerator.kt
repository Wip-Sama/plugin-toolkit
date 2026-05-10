package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.wip.plugintoolkit.api.*
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName

object KotlinGenerator {
    private val API_PACKAGE = "org.wip.plugintoolkit.api"
    
    // API Classes
    private val CN_PLUGIN_MANIFEST = PluginManifest::class.asClassName()
    private val CN_PLUGIN_INFO = PluginInfo::class.asClassName()
    private val CN_REQUIREMENTS = Requirements::class.asClassName()
    private val CN_CAPABILITY = Capability::class.asClassName()
    private val CN_PLUGIN_ACTION = PluginAction::class.asClassName()
    private val CN_PARAMETER_METADATA = ParameterMetadata::class.asClassName()
    private val CN_PARAMETER_CONSTRAINTS = ParameterConstraints::class.asClassName()
    private val CN_DATA_PROCESSOR = DataProcessor::class.asClassName()
    private val CN_PLUGIN_REQUEST = PluginRequest::class.asClassName()
    private val CN_PLUGIN_RESPONSE = PluginResponse::class.asClassName()
    private val CN_PLUGIN_ENTRY = PluginEntry::class.asClassName()
    private val CN_SETTING_METADATA = SettingMetadata::class.asClassName()
    private val CN_JOB_HANDLE = JobHandle::class.asClassName()
    private val CN_PLUGIN_SIGNAL = PluginSignal::class.asClassName()
    private val CN_PLUGIN_CONTEXT = PluginContext::class.asClassName()
    private val CN_PLUGIN_LOGGER = PluginLogger::class.asClassName()
    private val CN_PLUGIN_FILESYSTEM = PluginFileSystem::class.asClassName()
    private val CN_PROGRESS_REPORTER = ProgressReporter::class.asClassName()
    private val CN_EXECUTION_RESULT = ExecutionResult::class.asClassName()
    private val CN_EXECUTION_RESULT_SUCCESS = ExecutionResult.Success::class.asClassName()
    private val CN_EXECUTION_RESULT_ERROR = ExecutionResult.Error::class.asClassName()
    
    // Functions and Members
    private val MN_GET_DATA_TYPE = MemberName(API_PACKAGE, "getDataType")
    private val MN_JSON = Json::class.asClassName()
    private val MN_DECODE_FROM_JSON_ELEMENT = MemberName("kotlinx.serialization.json", "decodeFromJsonElement")
    private val MN_ENCODE_FROM_JSON_ELEMENT = MemberName("kotlinx.serialization.json", "encodeToJsonElement")
    
    private val CN_COROUTINE_SCOPE = CoroutineScope::class.asClassName()
    private val CN_DISPATCHERS = Dispatchers::class.asClassName()
    private val MN_SUPERVISOR_JOB = MemberName("kotlinx.coroutines", "SupervisorJob")
    private val MN_ASYNC = MemberName("kotlinx.coroutines", "async")
    private val MN_LAUNCH = MemberName("kotlinx.coroutines", "launch")
    private val MN_CANCEL = MemberName("kotlinx.coroutines", "cancel")

    fun generate(
        packageName: String,
        baseClassName: String,
        id: String,
        name: String,
        version: String,
        description: String,
        minMemoryMb: Int,
        minExecutionTimeMs: Int,
        functions: List<KSFunctionDeclaration>,
        settingsProperties: List<KSPropertyDeclaration>,
        actions: List<KSFunctionDeclaration>,
        classDeclaration: KSClassDeclaration
    ): FileSpec {
        val generatedFileName = baseClassName + "Generated"
        val manifestName = baseClassName + "Manifest"
        val dispatcherName = baseClassName + "Dispatcher"
        val entryName = baseClassName + "PluginEntry"

        val fileSpec = FileSpec.builder(packageName, generatedFileName)

        // 1. Generate Manifest Object
        val setupFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_SETUP_ANNOTATION) } }
        val updateFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_UPDATE_ANNOTATION) } }
        
        val manifestType = generateManifestObject(manifestName, id, name, version, description, minMemoryMb, minExecutionTimeMs, functions, settingsProperties, actions, updateFunction != null, setupFunction != null)
        fileSpec.addType(manifestType)

        // 2. Generate Dispatcher Class
        val dispatcherType = generateDispatcherClass(dispatcherName, packageName, baseClassName, functions, actions)
        fileSpec.addType(dispatcherType)

        // 3. Generate Entry Class
        val entryType = generateEntryClass(entryName, packageName, baseClassName, manifestName, dispatcherName, classDeclaration)
        fileSpec.addType(entryType)

        // 4. Generate Action Registry
        val registryName = baseClassName + "Actions"
        val registryType = generateActionRegistry(registryName, actions)
        fileSpec.addType(registryType)

        return fileSpec.build()
    }

    private fun generateManifestObject(
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
                
                val typeStr = paramType.toString()
                val isInfrastructure = typeStr == "org.wip.plugintoolkit.api.PluginLogger" || 
                                     typeStr == "org.wip.plugintoolkit.api.ProgressReporter" || 
                                     typeStr == "org.wip.plugintoolkit.api.PluginFileSystem" || 
                                     typeStr == "org.wip.plugintoolkit.api.PluginContext"

                if (!isInfrastructure) {
                    val defaultValueCode = if (defaultValue.isNotEmpty()) {
                        CodeBlock.of("%T.parseToJsonElement(%S)", MN_JSON, defaultValue)
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
                CodeBlock.of("%T.parseToJsonElement(%S)", MN_JSON, defaultVal)
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

    private fun generateDispatcherClass(
        dispatcherName: String,
        packageName: String,
        baseClassName: String,
        functions: List<KSFunctionDeclaration>,
        actions: List<KSFunctionDeclaration>
    ): TypeSpec {
        val dispatcherType = TypeSpec.classBuilder(dispatcherName)
            .addSuperinterface(CN_DATA_PROCESSOR)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("processor", ClassName(packageName, baseClassName))
                    .build()
            )
            .addProperty(
                PropertySpec.builder("processor", ClassName(packageName, baseClassName))
                    .initializer("processor")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("context", CN_PLUGIN_CONTEXT.copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("isDebug", Boolean::class)
                    .mutable(true)
                    .initializer("false")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(
                FunSpec.builder("setDebug")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("isDebug", Boolean::class)
                    .addStatement("this.isDebug = isDebug")
                    .build()
            )
            .addFunction(
                FunSpec.builder("setPluginContext")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("context", CN_PLUGIN_CONTEXT)
                    .addStatement("this.context = context")
                    .build()
            )

        // Map of handlers
        val mapType = ClassName("kotlin.collections", "Map").parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(CN_PLUGIN_REQUEST)),
                returnType = CN_EXECUTION_RESULT
            ).copy(suspending = true)
        )

        val mapCode = CodeBlock.builder()
        mapCode.add("mapOf(\n")
        mapCode.indent()
        
        functions.forEachIndexed { index, func ->
            val capAnn = func.annotations.first { it.hasQualifiedName(CAPABILITY_ANNOTATION) }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
                val methodName = func.simpleName.asString()
            
            mapCode.add("%S to { request ->\n", capName.lowercase())
            mapCode.indent()
            mapCode.add("val result = processor.%L(\n", methodName)
            mapCode.indent()
            
            val paramsList = func.parameters
            paramsList.forEachIndexed { pIndex, param ->
                val paramName = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                val typeStr = paramType.toString()
                val isNullable = param.type.resolve().isMarkedNullable
                val hasDefault = param.hasDefault
                
                if (typeStr == "org.wip.plugintoolkit.api.PluginLogger") {
                   mapCode.add("context?.logger ?: throw IllegalStateException(%S)", "Logger not available")
                } else if (typeStr == "org.wip.plugintoolkit.api.ProgressReporter") {
                   mapCode.add("context?.progress ?: throw IllegalStateException(%S)", "Progress reporter not available")
                } else if (typeStr == "org.wip.plugintoolkit.api.PluginFileSystem") {
                    mapCode.add("context?.fileSystem ?: throw IllegalStateException(%S)", "FileSystem not available")
                } else if (typeStr == "org.wip.plugintoolkit.api.PluginContext") {
                    mapCode.add("context ?: throw IllegalStateException(%S)", "PluginContext not available")
                } else if (param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) }) {
                    mapCode.add("request.resumeState?.let { %T.%M<%T>(it) }", MN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType)
                } else if (hasDefault) {
                    mapCode.add("if (request.parameters.containsKey(%S)) %T.%M<%T>(request.parameters[%S]!!) else null", paramName, MN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType.copy(nullable = false), paramName)
                } else if (isNullable) {
                    mapCode.add("request.parameters[%S]?.let { %T.%M<%T>(it) }", paramName, MN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType)
                } else {
                    mapCode.add("%T.%M<%T>(request.parameters[%S] ?: throw IllegalArgumentException(%S))", MN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType, paramName, "Missing mandatory parameter: $paramName")
                }
                if (pIndex < paramsList.size - 1) mapCode.add(",\n") else mapCode.add("\n")
            }
            mapCode.unindent()
            mapCode.add(")\n")
            
            val returnType = func.returnType?.resolve()?.toTypeName()
            if (returnType == CN_EXECUTION_RESULT) {
                mapCode.add("result\n")
            } else {
                mapCode.add("%T.Success(%T(result = %T.%M(result), metadata = mapOf(\"status\" to \"success\")))\n", CN_EXECUTION_RESULT, CN_PLUGIN_RESPONSE, MN_JSON, MN_ENCODE_FROM_JSON_ELEMENT)
            }
            mapCode.unindent()
            mapCode.add("}")
            if (index < functions.size - 1) mapCode.add(",\n") else mapCode.add("\n")
        }
        
        mapCode.unindent()
        mapCode.add(")\n")
        
        dispatcherType.addProperty(
            PropertySpec.builder("handlers", mapType)
                .initializer(mapCode.build())
                .addModifiers(KModifier.PRIVATE)
                .build()
        )

        val processFunc = FunSpec.builder("process")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("request", CN_PLUGIN_REQUEST)
            .returns(CN_EXECUTION_RESULT)
            .beginControlFlow("return try")
            .addStatement("val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException(%S)", "Unknown method: \${request.method}")
            .addStatement("handler(request)")
            .nextControlFlow("catch (e: Exception)")
            .addStatement("%T.Error(e.message ?: \"Unknown error\", e)", CN_EXECUTION_RESULT)
            .endControlFlow()
        
        dispatcherType.addFunction(processFunc.build())

        val processAsyncFunc = FunSpec.builder("processAsync")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("request", CN_PLUGIN_REQUEST)
            .returns(CN_JOB_HANDLE)
            .addCode(CodeBlock.builder()
                .addStatement("val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException(%S)", "Unknown method: \${request.method}")
                .addStatement("val scope = %T(%T.Default + %M())", CN_COROUTINE_SCOPE, CN_DISPATCHERS, MN_SUPERVISOR_JOB)
                .beginControlFlow("val deferred = scope.%M", MN_ASYNC)
                .beginControlFlow("try")
                .addStatement("handler(request)")
                .nextControlFlow("catch (e: Exception)")
                .addStatement("%T.Error(e.message ?: \"Unknown error\", e)", CN_EXECUTION_RESULT)
                .endControlFlow()
                .endControlFlow()
                .add("\n")
                .beginControlFlow("return object : %T", CN_JOB_HANDLE)
                .addStatement("override val result = deferred")
                .beginControlFlow("override fun pause()")
                .addStatement("scope.%M { context?.signals?.sendSignal(%T.PAUSE) }", MN_LAUNCH, CN_PLUGIN_SIGNAL)
                .endControlFlow()
                .beginControlFlow("override fun cancel(force: Boolean)")
                .addStatement("scope.%M { context?.signals?.sendSignal(%T.CANCEL) }", MN_LAUNCH, CN_PLUGIN_SIGNAL)
                .addStatement("deferred.cancel()")
                .beginControlFlow("if (force)")
                .addStatement("scope.%M()", MN_CANCEL)
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .build())
        
        dispatcherType.addFunction(processAsyncFunc.build())

        // RunAction
        val actionMapType = ClassName("kotlin.collections", "Map").parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(CN_PLUGIN_CONTEXT)),
                returnType = ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName())
            ).copy(suspending = true)
        )

        val actionMapCode = CodeBlock.builder()
        actionMapCode.add("mapOf(\n")
        actionMapCode.indent()
        actions.forEachIndexed { index, func ->
            val methodName = func.simpleName.asString()
            actionMapCode.add("%S to { context ->\n", methodName.lowercase())
            actionMapCode.indent()
            val actParams = func.parameters
            val actArgs = actParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    CN_PLUGIN_FILESYSTEM.toString() -> "context.fileSystem"
                    CN_PLUGIN_LOGGER.toString() -> "context.logger"
                    CN_PROGRESS_REPORTER.toString() -> "context.progress"
                    CN_PLUGIN_CONTEXT.toString() -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            actionMapCode.add("runCatching { processor.%L($actArgs) }\n", methodName)
            actionMapCode.unindent()
            actionMapCode.add("}")
            if (index < actions.size - 1) actionMapCode.add(",\n") else actionMapCode.add("\n")
        }
        actionMapCode.unindent()
        actionMapCode.add(")\n")

        dispatcherType.addProperty(
            PropertySpec.builder("actionHandlers", actionMapType)
                .initializer(actionMapCode.build())
                .addModifiers(KModifier.PRIVATE)
                .build()
        )

        val runActionFunc = FunSpec.builder("runAction")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("action", CN_PLUGIN_ACTION)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))
            .addStatement("val handler = actionHandlers[action.functionName.lowercase()] ?: return Result.failure(IllegalArgumentException(\"Unknown action: \${action.functionName}\"))")
            .addStatement("return handler(context)")
        
        dispatcherType.addFunction(runActionFunc.build())

        return dispatcherType.build()
    }

    private fun generateEntryClass(
        entryName: String,
        packageName: String,
        baseClassName: String,
        manifestName: String,
        dispatcherName: String,
        classDeclaration: KSClassDeclaration
    ): TypeSpec {
        val entryType = TypeSpec.classBuilder(entryName)
            .addSuperinterface(CN_PLUGIN_ENTRY)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("processor", ClassName(packageName, baseClassName))
                            .defaultValue("%T()", ClassName(packageName, baseClassName))
                            .build()
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder("processor", ClassName(packageName, baseClassName))
                    .initializer("processor")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("dispatcher", ClassName(packageName, dispatcherName))
                    .initializer("%T(processor)", ClassName(packageName, dispatcherName))
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("isDebug", Boolean::class)
                    .mutable(true)
                    .initializer("false")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(
                FunSpec.builder("getKoinModule")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("org.koin.core.module", "Module"))
                    .addCode("return org.koin.dsl.module {\n  single { processor }\n  single<%T> { this@%L }\n}\n", CN_PLUGIN_ENTRY, entryName)
                    .build()
            )
            .addFunction(
                FunSpec.builder("getProcessor")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(CN_DATA_PROCESSOR)
                    .addStatement("return dispatcher")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getManifest")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(CN_PLUGIN_MANIFEST)
                    .addStatement("return %L.manifest", manifestName)
                    .build()
            )
            .addFunction(
                FunSpec.builder("setDebug")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("isDebug", Boolean::class)
                    .addStatement("this.isDebug = isDebug")
                    .addStatement("dispatcher.setDebug(isDebug)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("shutdown")
                    .addModifiers(KModifier.OVERRIDE)
                    .build()
            )

        val setupFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_SETUP_ANNOTATION) } }
        val validateFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_VALIDATE_ANNOTATION) } }
        val loadFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_LOAD_ANNOTATION) } }
        val updateFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_UPDATE_ANNOTATION) } }

        // performLoad
        val loadFunBuilder = FunSpec.builder("performLoad")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))

        if (loadFunction != null) {
            val loadParams = loadFunction.parameters
            val callArgs = loadParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    CN_PLUGIN_FILESYSTEM.toString() -> "context.fileSystem"
                    CN_PLUGIN_LOGGER.toString() -> "context.logger"
                    CN_PROGRESS_REPORTER.toString() -> "context.progress"
                    CN_PLUGIN_CONTEXT.toString() -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            loadFunBuilder.addStatement("return processor.${loadFunction.simpleName.asString()}($callArgs)")
        } else {
            loadFunBuilder.addStatement("return Result.success(Unit)")
        }
        entryType.addFunction(loadFunBuilder.build())

        // performSetup
        val setupFunBuilder = FunSpec.builder("performSetup")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))

        if (setupFunction != null) {
            val setupParams = setupFunction.parameters
            val callArgs = setupParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    CN_PLUGIN_FILESYSTEM.toString() -> "context.fileSystem"
                    CN_PLUGIN_LOGGER.toString() -> "context.logger"
                    CN_PROGRESS_REPORTER.toString() -> "context.progress"
                    CN_PLUGIN_CONTEXT.toString() -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            setupFunBuilder.addStatement("return processor.${setupFunction.simpleName.asString()}($callArgs)")
        } else {
            setupFunBuilder.addStatement("return Result.success(Unit)")
        }
        entryType.addFunction(setupFunBuilder.build())

        // performValidate
        val validateFunBuilder = FunSpec.builder("validate")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))

        if (validateFunction != null) {
            val validateParams = validateFunction.parameters
            val callArgs = validateParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    CN_PLUGIN_FILESYSTEM.toString() -> "context.fileSystem"
                    CN_PLUGIN_LOGGER.toString() -> "context.logger"
                    CN_PROGRESS_REPORTER.toString() -> "context.progress"
                    CN_PLUGIN_CONTEXT.toString() -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            validateFunBuilder.addStatement("return processor.${validateFunction.simpleName.asString()}($callArgs)")
        } else {
            validateFunBuilder.addStatement("return Result.success(Unit)")
        }
        entryType.addFunction(validateFunBuilder.build())
        
        // performUpdate
        val updateFunBuilder = FunSpec.builder("performUpdate")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))

        if (updateFunction != null) {
            val updateParams = updateFunction.parameters
            val callArgs = updateParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    CN_PLUGIN_FILESYSTEM.toString() -> "context.fileSystem"
                    CN_PLUGIN_LOGGER.toString() -> "context.logger"
                    CN_PROGRESS_REPORTER.toString() -> "context.progress"
                    CN_PLUGIN_CONTEXT.toString() -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            updateFunBuilder.addStatement("return processor.${updateFunction.simpleName.asString()}($callArgs)")
        } else {
            updateFunBuilder.addStatement("return Result.success(Unit)")
        }
        entryType.addFunction(updateFunBuilder.build())

        return entryType.build()
    }

    private fun generateActionRegistry(registryName: String, actions: List<KSFunctionDeclaration>): TypeSpec {
        val registryType = TypeSpec.objectBuilder(registryName)
        
        actions.forEach { func ->
            val ann = func.annotations.first { it.hasQualifiedName(PLUGIN_ACTION_ANNOTATION) }
            val actName = ann.arguments.find { it.name?.asString() == "name" }?.value as String
            val constName = actName.uppercase().replace(" ", "_")
            
            registryType.addProperty(
                PropertySpec.builder(constName, String::class)
                    .addModifiers(KModifier.CONST)
                    .initializer("%S", func.simpleName.asString())
                    .build()
            )
        }
        
        return registryType.build()
    }
}
