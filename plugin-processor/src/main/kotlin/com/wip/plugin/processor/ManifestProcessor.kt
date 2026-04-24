package com.wip.plugin.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.wip.plugin.api.annotations.PluginModule
import com.wip.plugin.api.annotations.Capability
import com.wip.plugin.api.annotations.PluginParam

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
        val symbols = resolver.getSymbolsWithAnnotation(PluginModule::class.qualifiedName!!)
        
        symbols.filterIsInstance<KSClassDeclaration>().forEach { classDeclaration ->
            generateManifest(classDeclaration, resolver)
        }
        
        return emptyList()
    }

    private fun generateManifest(classDeclaration: KSClassDeclaration, resolver: Resolver) {
        val pluginModuleAnnotation = classDeclaration.annotations.find { 
            it.shortName.asString() == "PluginModule" 
        } ?: return

        val id = pluginModuleAnnotation.arguments.find { it.name?.asString() == "id" }?.value as String
        val name = pluginModuleAnnotation.arguments.find { it.name?.asString() == "name" }?.value as String
        val version = pluginModuleAnnotation.arguments.find { it.name?.asString() == "version" }?.value as String
        val description = pluginModuleAnnotation.arguments.find { it.name?.asString() == "description" }?.value as String
        val minMemoryMb = pluginModuleAnnotation.arguments.find { it.name?.asString() == "minMemoryMb" }?.value as Int
        val minExecutionTimeMs = pluginModuleAnnotation.arguments.find { it.name?.asString() == "minExecutionTimeMs" }?.value as Int

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
            .addImport("com.wip.plugin.api", "PluginManifest", "ModuleInfo", "Requirements", "Capability", "ParameterMetadata", "DataProcessor", "PluginRequest", "PluginResponse", "PluginEntry", "getDataType")
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
                val paramAnn = param.annotations.find { it.shortName.asString() == "PluginParam" }
                val paramDesc = paramAnn?.arguments?.find { it.name?.asString() == "description" }?.value as? String ?: ""
                val paramNameStr = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                
                capabilitiesCode.add("%S to ParameterMetadata(null, %S, getDataType<%T>())", paramNameStr, paramDesc, paramType)
                if (pIndex < paramsList.size - 1) capabilitiesCode.add(",\n") else capabilitiesCode.add("\n")
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

        manifestType.addProperty(
            PropertySpec.builder("manifest", ClassName("com.wip.plugin.api", "PluginManifest"))
                .initializer(
                    CodeBlock.builder()
                        .add("PluginManifest(\n")
                        .indent()
                        .add("manifestVersion = %S,\n", "1.0")
                        .add("module = ModuleInfo(id = %S, name = %S, version = %S, description = %S),\n", id, name, version, description)
                        .add("requirements = Requirements(minMemoryMb = %L, minExecutionTimeMs = %L),\n", minMemoryMb, minExecutionTimeMs)
                        .add("capabilities = ")
                        .add(capabilitiesCode.build())
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
                val isNullable = param.type.resolve().isMarkedNullable
                val hasDefault = param.hasDefault
                
                if (hasDefault) {
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
    }
}
