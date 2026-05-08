package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName

object KotlinGenerator {
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
            .addImport("org.wip.plugintoolkit.api", "PluginManifest", "PluginInfo", "Requirements", "Capability", "PluginAction", "ParameterMetadata", "ParameterConstraints", "DataProcessor", "PluginRequest", "PluginResponse", "PluginEntry", "getDataType", "SettingMetadata", "UpdateType", "JobHandle", "PluginSignal", "PluginContext", "PluginLogger", "PluginFileSystem", "ProgressReporter", "ExecutionResult")
            .addImport("kotlinx.serialization.json", "Json", "decodeFromJsonElement", "encodeToJsonElement")
            .addImport("kotlinx.coroutines", "CoroutineScope", "Dispatchers", "SupervisorJob", "async", "launch", "cancel")

        // 1. Generate Manifest Object
        val manifestType = generateManifestObject(manifestName, id, name, version, description, minMemoryMb, minExecutionTimeMs, functions, settingsProperties, actions)
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
        actions: List<KSFunctionDeclaration>
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

            capabilitiesCode.add("Capability(\n")
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
                    val defaultValueCode = if (defaultValue.isNotEmpty()) "Json.parseToJsonElement(%S)" else "%L"
                    val defaultValueVal = defaultValue.ifEmpty { "null" }
                    
                    val minValue = paramAnn?.arguments?.find { it.name?.asString() == "minValue" }?.value as? Double ?: Double.NaN
                    val maxValue = paramAnn?.arguments?.find { it.name?.asString() == "maxValue" }?.value as? Double ?: Double.NaN
                    val minLength = paramAnn?.arguments?.find { it.name?.asString() == "minLength" }?.value as? Int ?: -1
                    val maxLength = paramAnn?.arguments?.find { it.name?.asString() == "maxLength" }?.value as? Int ?: -1
                    val regex = paramAnn?.arguments?.find { it.name?.asString() == "regex" }?.value as? String ?: ""
                    val multiSelect = paramAnn?.arguments?.find { it.name?.asString() == "multiSelect" }?.value as? Boolean ?: false
                    val minChoices = paramAnn?.arguments?.find { it.name?.asString() == "minChoices" }?.value as? Int ?: -1
                    val maxChoices = paramAnn?.arguments?.find { it.name?.asString() == "maxChoices" }?.value as? Int ?: -1
                    
                    val hasConstraints = !minValue.isNaN() || !maxValue.isNaN() || minLength != -1 || maxLength != -1 || regex.isNotEmpty() || multiSelect || minChoices != -1 || maxChoices != -1
                    
                    val constraintsCode = if (hasConstraints) {
                        "ParameterConstraints(minValue = ${if (!minValue.isNaN()) minValue else "null"}, maxValue = ${if (!maxValue.isNaN()) maxValue else "null"}, minLength = ${if (minLength != -1) minLength else "null"}, maxLength = ${if (maxLength != -1) maxLength else "null"}, regex = ${if (regex.isNotEmpty()) "\"$regex\"" else "null"}, multiSelect = ${if (multiSelect) "true" else "null"}, minChoices = ${if (minChoices != -1) minChoices else "null"}, maxChoices = ${if (maxChoices != -1) maxChoices else "null"})"
                    } else "null"
                    
                    capabilitiesCode.add("%S to ParameterMetadata(defaultValue = $defaultValueCode, description = %S, type = getDataType<%T>(), constraints = $constraintsCode)", paramNameStr, defaultValueVal, paramDesc, paramType)
                    if (pIndex < paramsList.size - 1) capabilitiesCode.add(",\n") else capabilitiesCode.add("\n")
                }
            }
            capabilitiesCode.unindent()
            capabilitiesCode.add("),\n")
            
            val returnType = func.returnType?.resolve()?.toTypeName() ?: UNIT
            capabilitiesCode.add("returnType = getDataType<%T>()\n", returnType)
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
            val propName = prop.simpleName.asString()
            val propType = prop.type.resolve().toTypeName()
            val defaultValueCode = if (defaultVal.isNotEmpty()) "Json.parseToJsonElement(%S)" else "null"
            
            if (defaultVal.isNotEmpty()) {
               settingsCode.add("%S to SettingMetadata($defaultValueCode, %S, getDataType<%T>())", propName, defaultVal, desc, propType)
            } else {
               settingsCode.add("%S to SettingMetadata(null, %S, getDataType<%T>())", propName, desc, propType)
            }
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
            
            actionsCode.add("PluginAction(name = %S, description = %S, functionName = %S)", actName, actDesc, func.simpleName.asString())
            if (index < actions.size - 1) actionsCode.add(",\n") else actionsCode.add("\n")
        }
        actionsCode.unindent()
        actionsCode.add(")\n")

        manifestType.addProperty(
            PropertySpec.builder("manifest", ClassName("org.wip.plugintoolkit.api", "PluginManifest"))
                .initializer(
                    CodeBlock.builder()
                        .add("PluginManifest(\n")
                        .indent()
                        .add("manifestVersion = %S,\n", "1.0")
                        .add("plugin = PluginInfo(id = %S, name = %S, version = %S, description = %S),\n", id, name, version, description)
                        .add("requirements = Requirements(minMemoryMb = %L, minExecutionTimeMs = %L),\n", minMemoryMb, minExecutionTimeMs)
                        .add("capabilities = ")
                        .add(capabilitiesCode.build())
                        .add(",\nactions = ")
                        .add(actionsCode.build())
                        .add(",\nsettings = ")
                        .add(settingsCode.build())
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
            .addSuperinterface(ClassName("org.wip.plugintoolkit.api", "DataProcessor"))
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
                PropertySpec.builder("context", ClassName("org.wip.plugintoolkit.api", "PluginContext").copy(nullable = true))
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
                    .addParameter("context", ClassName("org.wip.plugintoolkit.api", "PluginContext"))
                    .addStatement("this.context = context")
                    .build()
            )

        // Map of handlers
        val mapType = ClassName("kotlin.collections", "Map").parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(ClassName("org.wip.plugintoolkit.api", "PluginRequest"))),
                returnType = ClassName("org.wip.plugintoolkit.api", "ExecutionResult")
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
                    mapCode.add("request.resumeState?.let { Json.decodeFromJsonElement<%T>(it) }", paramType)
                } else if (hasDefault) {
                    mapCode.add("if (request.parameters.containsKey(%S)) Json.decodeFromJsonElement<%T>(request.parameters[%S]!!) else null", paramName, paramType.copy(nullable = false), paramName)
                } else if (isNullable) {
                    mapCode.add("request.parameters[%S]?.let { Json.decodeFromJsonElement<%T>(it) }", paramName, paramType)
                } else {
                    mapCode.add("Json.decodeFromJsonElement<%T>(request.parameters[%S] ?: throw IllegalArgumentException(%S))", paramType, paramName, "Missing mandatory parameter: $paramName")
                }
                if (pIndex < paramsList.size - 1) mapCode.add(",\n") else mapCode.add("\n")
            }
            mapCode.unindent()
            mapCode.add(")\n")
            
            val returnType = func.returnType?.resolve()?.toTypeName()?.toString()
            if (returnType == "org.wip.plugintoolkit.api.ExecutionResult") {
                mapCode.add("result\n")
            } else {
                mapCode.add("ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf(\"status\" to \"success\")))\n")
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
            .addParameter("request", ClassName("org.wip.plugintoolkit.api", "PluginRequest"))
            .returns(ClassName("org.wip.plugintoolkit.api", "ExecutionResult"))
            .beginControlFlow("return try")
            .addStatement("val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException(%S)", "Unknown method: \${request.method}")
            .addStatement("handler(request)")
            .nextControlFlow("catch (e: Exception)")
            .addStatement("ExecutionResult.Error(e.message ?: \"Unknown error\", e)")
            .endControlFlow()
        
        dispatcherType.addFunction(processFunc.build())

        val processAsyncFunc = FunSpec.builder("processAsync")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("request", ClassName("org.wip.plugintoolkit.api", "PluginRequest"))
            .returns(ClassName("org.wip.plugintoolkit.api", "JobHandle"))
            .addCode("""
                val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException("Unknown method: ${'$'}{request.method}")
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val deferred = scope.async {
                    try {
                        handler(request)
                    } catch (e: Exception) {
                        ExecutionResult.Error(e.message ?: "Unknown error", e)
                    }
                }
                
                return object : JobHandle {
                    override val result = deferred
                    override fun pause() {
                        scope.launch { context?.signals?.sendSignal(PluginSignal.PAUSE) }
                    }
                    override fun cancel(force: Boolean) {
                        scope.launch { context?.signals?.sendSignal(PluginSignal.CANCEL) }
                        deferred.cancel()
                        if (force) {
                            scope.cancel()
                        }
                    }
                }
            """.trimIndent())
        
        dispatcherType.addFunction(processAsyncFunc.build())

        // RunAction
        val actionMapType = ClassName("kotlin.collections", "Map").parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(ClassName("org.wip.plugintoolkit.api", "PluginContext"))),
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
                    "org.wip.plugintoolkit.api.PluginFileSystem" -> "context.fileSystem"
                    "org.wip.plugintoolkit.api.PluginLogger" -> "context.logger"
                    "org.wip.plugintoolkit.api.ProgressReporter" -> "context.progress"
                    "org.wip.plugintoolkit.api.PluginContext" -> "context"
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
            .addParameter("action", ClassName("org.wip.plugintoolkit.api", "PluginAction"))
            .addParameter("context", ClassName("org.wip.plugintoolkit.api", "PluginContext"))
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
            .addSuperinterface(ClassName("org.wip.plugintoolkit.api", "PluginEntry"))
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
                    .addCode("return org.koin.dsl.module {\n  single { processor }\n  single<PluginEntry> { this@%L }\n}\n", entryName)
                    .build()
            )
            .addFunction(
                FunSpec.builder("getProcessor")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("org.wip.plugintoolkit.api", "DataProcessor"))
                    .addStatement("return dispatcher")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getManifest")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("org.wip.plugintoolkit.api", "PluginManifest"))
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

        // 3. performSetup
        val setupFunBuilder = FunSpec.builder("performSetup")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", ClassName("org.wip.plugintoolkit.api", "PluginContext"))
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))

        if (setupFunction != null) {
            val setupParams = setupFunction.parameters
            val callArgs = setupParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    "org.wip.plugintoolkit.api.PluginFileSystem" -> "context.fileSystem"
                    "org.wip.plugintoolkit.api.PluginLogger" -> "context.logger"
                    "org.wip.plugintoolkit.api.ProgressReporter" -> "context.progress"
                    "org.wip.plugintoolkit.api.PluginContext" -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            setupFunBuilder.addStatement("return processor.${setupFunction.simpleName.asString()}($callArgs)")
        } else {
            setupFunBuilder.addStatement("return Result.success(Unit)")
        }
        entryType.addFunction(setupFunBuilder.build())

        // 4. validate
        val validateFunBuilder = FunSpec.builder("validate")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", ClassName("org.wip.plugintoolkit.api", "PluginContext"))
            .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))

        if (validateFunction != null) {
            val validateParams = validateFunction.parameters
            val callArgs = validateParams.joinToString(", ") { param ->
                val t = param.type.resolve().toTypeName().toString()
                when (t) {
                    "org.wip.plugintoolkit.api.PluginFileSystem" -> "context.fileSystem"
                    "org.wip.plugintoolkit.api.PluginLogger" -> "context.logger"
                    "org.wip.plugintoolkit.api.ProgressReporter" -> "context.progress"
                    "org.wip.plugintoolkit.api.PluginContext" -> "context"
                    else -> param.name?.asString() ?: ""
                }
            }
            validateFunBuilder.addStatement("return processor.${validateFunction.simpleName.asString()}($callArgs)")
        } else {
            validateFunBuilder.addStatement("return Result.success(Unit)")
        }
        entryType.addFunction(validateFunBuilder.build())

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
