package com.wip.plugin.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.json.Json
import com.wip.plugin.api.PluginManifest
import com.wip.plugin.api.PluginInfo
import com.wip.plugin.api.annotations.PluginInfo as PluginInfoAnnotation
import com.wip.plugin.api.Requirements
import com.wip.plugin.api.DataType
import com.wip.plugin.api.PrimitiveType
import com.wip.plugin.api.Capability
import com.wip.plugin.api.ParameterMetadata
import com.wip.plugin.api.ParameterConstraints
import com.wip.plugin.api.SettingMetadata
import com.wip.plugin.api.Changelog
import com.wip.plugin.api.Release
import java.io.File

class ManifestProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ManifestProcessor(environment.codeGenerator, environment.logger)
    }
}

class ManifestProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(PluginInfoAnnotation::class.qualifiedName!!)
        
        symbols.filterIsInstance<KSClassDeclaration>().forEach { classDeclaration ->
            generateManifest(classDeclaration, resolver)
        }
        
        return emptyList()
    }

    private fun generateManifest(classDeclaration: KSClassDeclaration, resolver: Resolver) {
        val pluginInfoAnnotation = classDeclaration.annotations.find {
            it.shortName.asString() == "PluginInfo"
        } ?: return

        val id = pluginInfoAnnotation.arguments.find { it.name?.asString() == "id" }?.value as String
        val name = pluginInfoAnnotation.arguments.find { it.name?.asString() == "name" }?.value as String
        val version = pluginInfoAnnotation.arguments.find { it.name?.asString() == "version" }?.value as String
        val description = pluginInfoAnnotation.arguments.find { it.name?.asString() == "description" }?.value as String
        val minMemoryMb = pluginInfoAnnotation.arguments.find { it.name?.asString() == "minMemoryMb" }?.value as Int
        val minExecutionTimeMs = pluginInfoAnnotation.arguments.find { it.name?.asString() == "minExecutionTimeMs" }?.value as Int

        val functions = classDeclaration.getAllFunctions().filter { 
            it.annotations.any { ann -> ann.shortName.asString() == "Capability" }
        }

        val packageName = classDeclaration.packageName.asString()
        val baseClassName = classDeclaration.simpleName.asString()
        val generatedFileName = baseClassName + "Generated"
        val manifestName = baseClassName + "Manifest"
        val dispatcherName = baseClassName + "Dispatcher"
        val entryName = baseClassName + "PluginEntry"

        val fileSpec = FileSpec.builder(packageName, generatedFileName)
            .addImport("com.wip.plugin.api", "PluginManifest", "PluginInfo", "Requirements", "Capability", "ParameterMetadata", "ParameterConstraints", "DataProcessor", "PluginRequest", "PluginResponse", "PluginEntry", "getDataType", "SettingMetadata", "UpdateType")
            .addImport("kotlinx.serialization.json", "Json", "decodeFromJsonElement", "encodeToJsonElement")

        // 1. Generate Manifest Object
        val manifestType = TypeSpec.objectBuilder(manifestName)
        
        val capabilitiesCode = CodeBlock.builder()
        capabilitiesCode.add("listOf(\n")
        capabilitiesCode.indent()
        val functionsList = functions.toList()
        functionsList.forEachIndexed { index, func ->
            val capAnn = func.annotations.first { it.shortName.asString() == "Capability" }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val capDesc = capAnn.arguments.find { it.name?.asString() == "description" }?.value as String

            capabilitiesCode.add("Capability(\n")
            capabilitiesCode.indent()
            capabilitiesCode.add("name = %S,\n", capName)
            capabilitiesCode.add("description = %S,\n", capDesc)
            capabilitiesCode.add("parameters = mapOf(\n")
            capabilitiesCode.indent()
            val paramsList = func.parameters.toList()
            paramsList.forEachIndexed { pIndex, param ->
                val paramAnn = param.annotations.find { it.shortName.asString() == "CapabilityParam" }
                val paramDesc = paramAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                val defaultValue = paramAnn?.arguments?.find { it.name?.asString() == "defaultValue" }?.value as? String ?: ""
                val paramNameStr = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                
                // Only include in manifest if it's not a system-injected dependency
                val typeStr = paramType.toString()
                if (typeStr != "com.wip.plugin.api.PluginLogger" && typeStr != "com.wip.plugin.api.ProgressReporter" && typeStr != "com.wip.plugin.api.PluginFileSystem") {
                    val defaultValueCode = if (defaultValue.isNotEmpty()) "Json.parseToJsonElement(%S)" else "%L"
                    val defaultValueVal = if (defaultValue.isNotEmpty()) defaultValue else "null"
                    
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
            if (index < functionsList.size - 1) capabilitiesCode.add("),\n") else capabilitiesCode.add(")\n")
        }
        capabilitiesCode.unindent()
        capabilitiesCode.add(")\n")

        val settingsProperties = classDeclaration.getAllProperties().filter { 
            it.annotations.any { ann -> ann.shortName.asString() == "PluginSetting" }
        }

        val settingsCode = CodeBlock.builder()
        settingsCode.add("mapOf(\n")
        settingsCode.indent()
        val propsList = settingsProperties.toList()
        propsList.forEachIndexed { index, prop ->
            val ann = prop.annotations.first { it.shortName.asString() == "PluginSetting" }
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
            if (index < propsList.size - 1) settingsCode.add(",\n") else settingsCode.add("\n")
        }
        settingsCode.unindent()
        settingsCode.add(")\n")

        manifestType.addProperty(
            PropertySpec.builder("manifest", ClassName("com.wip.plugin.api", "PluginManifest"))
                .initializer(
                    CodeBlock.builder()
                        .add("PluginManifest(\n")
                        .indent()
                        .add("manifestVersion = %S,\n", "1.0")
                        .add("plugin = PluginInfo(id = %S, name = %S, version = %S, description = %S),\n", id, name, version, description)
                        .add("requirements = Requirements(minMemoryMb = %L, minExecutionTimeMs = %L),\n", minMemoryMb, minExecutionTimeMs)
                        .add("capabilities = ")
                        .add(capabilitiesCode.build())
                        .add(",\nsettings = ")
                        .add(settingsCode.build())
                        .unindent()
                        .add(")")
                        .build()
                )
                .build()
        )

        // 2. Generate Dispatcher Class
        val dispatcherType = TypeSpec.classBuilder(dispatcherName)
            .addSuperinterface(ClassName("com.wip.plugin.api", "DataProcessor"))
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
                PropertySpec.builder("context", ClassName("com.wip.plugin.api", "ExecutionContext").copy(nullable = true))
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
                FunSpec.builder("setExecutionContext")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("context", ClassName("com.wip.plugin.api", "ExecutionContext"))
                    .addStatement("this.context = context")
                    .build()
            )

        // Map of handlers
        val mapType = ClassName("kotlin.collections", "Map").parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(ClassName("com.wip.plugin.api", "PluginRequest"))),
                returnType = ClassName("com.wip.plugin.api", "PluginResponse")
            ).copy(suspending = true)
        )

        val mapCode = CodeBlock.builder()
        mapCode.add("mapOf(\n")
        mapCode.indent()
        
        functions.forEachIndexed { index, func ->
            val capAnn = func.annotations.first { it.shortName.asString() == "Capability" }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val methodName = func.simpleName.asString()
            
            mapCode.add("%S to { request ->\n", capName.lowercase())
            mapCode.indent()
            mapCode.add("val result = processor.%L(\n", methodName)
            mapCode.indent()
            
            val paramsList = func.parameters.toList()
            paramsList.forEachIndexed { pIndex, param ->
                val paramName = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                val typeStr = paramType.toString()
                val isNullable = param.type.resolve().isMarkedNullable
                val hasDefault = param.hasDefault
                
                if (typeStr == "com.wip.plugin.api.PluginLogger") {
                    mapCode.add("context?.logger ?: throw IllegalStateException(%S)", "Logger not available")
                } else if (typeStr == "com.wip.plugin.api.ProgressReporter") {
                    mapCode.add("context?.progress ?: throw IllegalStateException(%S)", "Progress reporter not available")
                } else if (typeStr == "com.wip.plugin.api.PluginFileSystem") {
                    mapCode.add("context?.fileSystem ?: throw IllegalStateException(%S)", "FileSystem not available")
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
            mapCode.add("PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf(\"status\" to \"success\"))\n")
            mapCode.unindent()
            mapCode.add("}")
            if (index < functionsList.size - 1) mapCode.add(",\n") else mapCode.add("\n")
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
            .addParameter("request", ClassName("com.wip.plugin.api", "PluginRequest"))
            .returns(ClassName("kotlin", "Result").parameterizedBy(ClassName("com.wip.plugin.api", "PluginResponse")))
            .beginControlFlow("return try")
            .addStatement("val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException(%S)", "Unknown method: \${request.method}")
            .addStatement("Result.success(handler(request))")
            .nextControlFlow("catch (e: Exception)")
            .addStatement("Result.failure(e)")
            .endControlFlow()
        
        dispatcherType.addFunction(processFunc.build())

        // 3. Generate Entry Class
        val entryType = TypeSpec.classBuilder(entryName)
            .addSuperinterface(ClassName("com.wip.plugin.api", "PluginEntry"))
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
                FunSpec.builder("initialize")
                    .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                    .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))
                    .addStatement("return Result.success(Unit)")
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
                    .returns(ClassName("com.wip.plugin.api", "DataProcessor"))
                    .addStatement("return dispatcher")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getManifest")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("com.wip.plugin.api", "PluginManifest"))
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

        val setupFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.shortName.asString() == "PluginSetup" } }
        val validateFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.shortName.asString() == "PluginValidate" } }

        if (setupFunction != null) {
            val setupParams = setupFunction.parameters.toList()
            val setupInjectsContext = setupParams.any {
                val t = it.type.resolve().toTypeName().toString()
                t == "com.wip.plugin.api.PluginFileSystem" || t == "com.wip.plugin.api.PluginLogger" || t == "com.wip.plugin.api.ProgressReporter" || t == "com.wip.plugin.api.ExecutionContext"
            }
            val setupFunBuilder = FunSpec.builder("performSetup")
                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                .addParameter("context", ClassName("com.wip.plugin.api", "ExecutionContext"))
                .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))
            
            if (setupInjectsContext) {
                // Build call with injected params
                val callArgs = setupParams.joinToString(", ") { param ->
                    val t = param.type.resolve().toTypeName().toString()
                    when (t) {
                        "com.wip.plugin.api.PluginFileSystem" -> "context.fileSystem"
                        "com.wip.plugin.api.PluginLogger" -> "context.logger"
                        "com.wip.plugin.api.ProgressReporter" -> "context.progress"
                        "com.wip.plugin.api.ExecutionContext" -> "context"
                        else -> param.name?.asString() ?: ""
                    }
                }
                setupFunBuilder.addStatement("return processor.${setupFunction.simpleName.asString()}($callArgs)")
            } else {
                setupFunBuilder.addStatement("return processor.${setupFunction.simpleName.asString()}()")
            }
            entryType.addFunction(setupFunBuilder.build())
        }

        if (validateFunction != null) {
            val validateParams = validateFunction.parameters.toList()
            val validateInjectsContext = validateParams.any {
                val t = it.type.resolve().toTypeName().toString()
                t == "com.wip.plugin.api.PluginFileSystem" || t == "com.wip.plugin.api.PluginLogger" || t == "com.wip.plugin.api.ProgressReporter" || t == "com.wip.plugin.api.ExecutionContext"
            }
            val validateFunBuilder = FunSpec.builder("validate")
                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                .addParameter("context", ClassName("com.wip.plugin.api", "ExecutionContext"))
                .returns(ClassName("kotlin", "Result").parameterizedBy(Unit::class.asClassName()))
            
            if (validateInjectsContext) {
                val callArgs = validateParams.joinToString(", ") { param ->
                    val t = param.type.resolve().toTypeName().toString()
                    when (t) {
                        "com.wip.plugin.api.PluginFileSystem" -> "context.fileSystem"
                        "com.wip.plugin.api.PluginLogger" -> "context.logger"
                        "com.wip.plugin.api.ProgressReporter" -> "context.progress"
                        "com.wip.plugin.api.ExecutionContext" -> "context"
                        else -> param.name?.asString() ?: ""
                    }
                }
                validateFunBuilder.addStatement("return processor.${validateFunction.simpleName.asString()}($callArgs)")
            } else {
                validateFunBuilder.addStatement("return processor.${validateFunction.simpleName.asString()}()")
            }
            entryType.addFunction(validateFunBuilder.build())
        }

        fileSpec.addType(manifestType.build())
        fileSpec.addType(dispatcherType.build())
        fileSpec.addType(entryType.build())

        val file = codeGenerator.createNewFile(
            Dependencies(true, classDeclaration.containingFile!!),
            packageName,
            generatedFileName
        )
        file.writer().use { 
            fileSpec.build().writeTo(it) 
        }

        // 4. Generate SPI Service File
        val serviceFile = codeGenerator.createNewFile(
            Dependencies(true, classDeclaration.containingFile!!),
            "",
            "META-INF/services/com.wip.plugin.api.PluginEntry",
            ""
        )
        serviceFile.writer().use { 
            it.write("$packageName.$entryName") 
        }

        // 5. Look for changelog.txt
        var changelogObj: Changelog? = null
        val sourceFile = classDeclaration.containingFile
        val changelogFile = sourceFile?.let { findChangelogFile(it) }
        
        if (changelogFile != null) {
            val content = changelogFile.readText()
            changelogObj = parseChangelog(content)
            
            // Bundle changelog.txt into resources
            val bundledChangelog = codeGenerator.createNewFile(
                Dependencies(true, sourceFile),
                "",
                "resources/changelog",
                "txt"
            )
            bundledChangelog.writer().use { it.write(content) }
        }

        // 6. Generate manifest.json Resource File
        val manifestJsonFile = codeGenerator.createNewFile(
            Dependencies(true, classDeclaration.containingFile!!),
            "",
            "META-INF/manifest",
            "json"
        )
        
        val manifestCapabilities = functions.map { func ->
            val capAnn = func.annotations.first { it.shortName.asString() == "Capability" }
            val capName = capAnn.arguments.find { it.name?.asString() == "name" }?.value as String
            val capDesc = capAnn.arguments.find { it.name?.asString() == "description" }?.value as String
            
            val params = func.parameters.filter { param ->
                val paramType = param.type.resolve().toTypeName().toString()
                paramType != "com.wip.plugin.api.PluginLogger" && paramType != "com.wip.plugin.api.ProgressReporter" && paramType != "com.wip.plugin.api.PluginFileSystem"
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
                    type = mapKSTypeToDataType(ksType),
                    constraints = constraints
                )
            }
            
            Capability(
                name = capName,
                description = capDesc,
                parameters = if (params.isEmpty()) null else params,
                returnType = mapKSTypeToDataType(func.returnType!!.resolve())
            )
        }.toList()

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
                type = mapKSTypeToDataType(ksType)
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
        manifestJsonFile.writer().use { 
            it.write(json.encodeToString(manifestObj)) 
        }
    }

    private fun findChangelogFile(sourceFile: KSFile): File? {
        var currentDir = File(sourceFile.filePath).parentFile
        while (currentDir != null) {
            // Check direct parents
            val changelog = File(currentDir, "changelog.txt")
            if (changelog.exists()) return changelog
            
            // Check src/main/resources
            val resourcesChangelog = File(currentDir, "src/main/resources/changelog.txt")
            if (resourcesChangelog.exists()) return resourcesChangelog

            // Stop at plugin root
            if (File(currentDir, "build.gradle.kts").exists() || File(currentDir, "build.gradle").exists()) {
                val modResources = File(currentDir, "src/main/resources/changelog.txt")
                if (modResources.exists()) return modResources
                return if (changelog.exists()) changelog else null
            }
            currentDir = currentDir.parentFile
        }
        return null
    }

    private fun parseChangelog(content: String): Changelog {
        val releases = mutableListOf<Release>()
        var currentVersion: String? = null
        var currentDate: String? = null
        var currentCategories = mutableMapOf<String, MutableList<String>>()
        var currentCategoryName: String? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.all { it == '-' }) return@forEach

            when {
                trimmed.startsWith("Version:", ignoreCase = true) -> {
                    if (currentVersion != null) {
                        releases.add(Release(currentVersion, currentDate ?: "", currentCategories.mapValues { it.value.toList() }))
                        currentCategories = mutableMapOf()
                        currentCategoryName = null
                    }
                    currentVersion = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("Date:", ignoreCase = true) -> {
                    currentDate = trimmed.substringAfter(":").trim()
                }
                !line.startsWith(" ") && trimmed.endsWith(":") -> {
                    val catName = trimmed.removeSuffix(":")
                    if (catName.equals("Version", ignoreCase = true) || catName.equals("Date", ignoreCase = true)) return@forEach
                    currentCategoryName = catName
                    currentCategories[currentCategoryName!!] = mutableListOf()
                }
                line.startsWith(" ") && trimmed.startsWith("-") -> {
                    val item = trimmed.removePrefix("-").trim()
                    currentCategoryName?.let { currentCategories[it]?.add(item) }
                }
            }
        }

        if (currentVersion != null) {
            releases.add(Release(currentVersion, currentDate ?: "", currentCategories.mapValues { it.value.toList() }))
        }

        return Changelog(releases)
    }

    private fun mapKSTypeToDataType(ksType: KSType): DataType {
        val declaration = ksType.declaration
        val qualifiedName = declaration.qualifiedName?.asString() ?: ""
        
        return when (qualifiedName) {
            "kotlin.Double", "kotlin.Float" -> DataType.Primitive(PrimitiveType.DOUBLE)
            "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Long" -> DataType.Primitive(PrimitiveType.INT)
            "kotlin.String", "kotlin.Char" -> DataType.Primitive(PrimitiveType.STRING)
            "kotlin.Boolean" -> DataType.Primitive(PrimitiveType.BOOLEAN)
            "kotlin.Unit" -> DataType.Primitive(PrimitiveType.UNIT)
            "kotlinx.serialization.json.JsonElement", "kotlin.Any" -> DataType.Primitive(PrimitiveType.ANY)
            "kotlin.collections.List", "kotlin.collections.MutableList" -> {
                val elementType = ksType.arguments.firstOrNull()?.type?.resolve()
                if (elementType != null) {
                    DataType.Array(mapKSTypeToDataType(elementType))
                } else {
                    DataType.Primitive(PrimitiveType.ANY)
                }
            }
            else -> {
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
                    val options = declaration.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.classKind == ClassKind.ENUM_ENTRY }
                        .map { it.simpleName.asString() }
                        .toList()
                    DataType.Enum(qualifiedName, options)
                } else {
                    DataType.Object(qualifiedName)
                }
            }
        }
    }
}
