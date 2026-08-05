package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_DATA_PROCESSOR
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_EXECUTION_RESULT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_ILLEGAL_ARGUMENT_EXCEPTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_JSON
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_MAP
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_ACTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_CONTEXT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_FILESYSTEM
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_LOGGER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_REQUEST
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_RESPONSE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PROGRESS_REPORTER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_RESULT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_DECODE_FROM_JSON_ELEMENT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_ENCODE_FROM_JSON_ELEMENT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.RESUME_STATE_ANNOTATION

object DispatcherGenerator {
    fun generateDispatcherClass(
        dispatcherName: String,
        packageName: String,
        baseClassName: String,
        functions: List<KSFunctionDeclaration>,
        actions: List<KSFunctionDeclaration>,
        classDeclaration: com.google.devtools.ksp.symbol.KSClassDeclaration
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
        val mapType = CN_MAP.parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(CN_PLUGIN_REQUEST), ParameterSpec.unnamed(CN_PLUGIN_CONTEXT)),
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

            mapCode.add("%S to { request, context ->\n", capName.lowercase())
            mapCode.indent()
            mapCode.add("val result = processor.%L(\n", methodName)
            mapCode.indent()

            val paramsList = func.parameters
            paramsList.forEachIndexed { pIndex, param ->
                val paramName = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                val isNullable = param.type.resolve().isMarkedNullable
                val hasDefault = param.hasDefault

                when {
                    paramType == CN_PLUGIN_LOGGER -> {
                        mapCode.add("context.logger")
                    }

                    paramType == CN_PROGRESS_REPORTER -> {
                        mapCode.add("context.progress")
                    }

                    paramType == CN_PLUGIN_FILESYSTEM -> {
                        mapCode.add("context.fileSystem")
                    }

                    paramType == org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_HOST_FILESYSTEM -> {
                        mapCode.add("context.hostFileSystem")
                    }

                    paramType == org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_EXECUTION_FILESYSTEM -> {
                        mapCode.add("context.executionFileSystem")
                    }

                    paramType == CN_PLUGIN_CONTEXT -> {
                        mapCode.add("context")
                    }

                    param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) } -> {
                        mapCode.add(
                            "request.resumeState?.let { %T.%M<%T>(it) }",
                            CN_JSON,
                            MN_DECODE_FROM_JSON_ELEMENT,
                            paramType
                        )
                    }

                    hasDefault -> {
                        mapCode.add(
                            "if (request.parameters.containsKey(%S)) %T.%M<%T>(request.parameters[%S]!!) else null",
                            paramName,
                            CN_JSON,
                            MN_DECODE_FROM_JSON_ELEMENT,
                            paramType.copy(nullable = false),
                            paramName
                        )
                    }

                    isNullable -> {
                        mapCode.add(
                            "request.parameters[%S]?.let { %T.%M<%T>(it) }",
                            paramName,
                            CN_JSON,
                            MN_DECODE_FROM_JSON_ELEMENT,
                            paramType
                        )
                    }

                    else -> {
                        mapCode.add(
                            "%T.%M<%T>(request.parameters[%S] ?: throw %T(%S))",
                            CN_JSON,
                            MN_DECODE_FROM_JSON_ELEMENT,
                            paramType,
                            paramName,
                            CN_ILLEGAL_ARGUMENT_EXCEPTION,
                            "Missing mandatory parameter: $paramName"
                        )
                    }
                }
                if (pIndex < paramsList.size - 1) mapCode.add(",\n") else mapCode.add("\n")
            }
            mapCode.unindent()
            mapCode.add(")\n")

            val returnType = func.returnType?.resolve()?.toTypeName()
            if (returnType == CN_EXECUTION_RESULT) {
                mapCode.add("result\n")
            } else {
                val outputs = org.wip.plugintoolkit.api.processor.GeneratorUtils.getCapabilityOutputs(func)
                if (outputs.size > 1) {
                    mapCode.add("val jsonResult = %T {\n", ClassName("kotlinx.serialization.json", "buildJsonObject"))
                    mapCode.indent()
                    outputs.forEach { out ->
                        mapCode.add(
                            "put(%S, %T.%M(result.%L))\n",
                            out.name,
                            CN_JSON,
                            MN_ENCODE_FROM_JSON_ELEMENT,
                            out.originalName
                        )
                    }
                    mapCode.unindent()
                    mapCode.add("}\n")
                    mapCode.add(
                        "%T.Success(%T(result = jsonResult, metadata = mapOf(\"status\" to \"success\")))\n",
                        CN_EXECUTION_RESULT,
                        CN_PLUGIN_RESPONSE
                    )
                } else if (outputs.size == 1 && outputs.first().name != "result") {
                    mapCode.add("val jsonResult = %T {\n", ClassName("kotlinx.serialization.json", "buildJsonObject"))
                    mapCode.indent()
                    mapCode.add("put(%S, %T.%M(result))\n", outputs.first().name, CN_JSON, MN_ENCODE_FROM_JSON_ELEMENT)
                    mapCode.unindent()
                    mapCode.add("}\n")
                    mapCode.add(
                        "%T.Success(%T(result = jsonResult, metadata = mapOf(\"status\" to \"success\")))\n",
                        CN_EXECUTION_RESULT,
                        CN_PLUGIN_RESPONSE
                    )
                } else {
                    mapCode.add(
                        "%T.Success(%T(result = %T.%M(result), metadata = mapOf(\"status\" to \"success\")))\n",
                        CN_EXECUTION_RESULT,
                        CN_PLUGIN_RESPONSE,
                        CN_JSON,
                        MN_ENCODE_FROM_JSON_ELEMENT
                    )
                }
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
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(CN_EXECUTION_RESULT)
            .beginControlFlow("return try")
            .addStatement(
                "val handler = handlers[request.method.lowercase()] ?: throw %T(%S)",
                CN_ILLEGAL_ARGUMENT_EXCEPTION,
                "Unknown method: \${request.method}"
            )
            .addStatement("handler(request, context)")
            .nextControlFlow("catch (e: Exception)")
            .addStatement("%T.Error(e.message ?: \"Unknown error\", e)", CN_EXECUTION_RESULT)
            .endControlFlow()

        dispatcherType.addFunction(processFunc.build())

        // RunAction
        val jsonElementType = ClassName("kotlinx.serialization.json", "JsonElement")
        val paramsMapType = CN_MAP.parameterizedBy(String::class.asClassName(), jsonElementType)
        val actionMapType = CN_MAP.parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(paramsMapType), ParameterSpec.unnamed(CN_PLUGIN_CONTEXT)),
                returnType = CN_RESULT.parameterizedBy(Unit::class.asClassName())
            ).copy(suspending = true)
        )

        val actionMapCode = CodeBlock.builder()
        actionMapCode.add("mapOf(\n")
        actionMapCode.indent()
        actions.forEachIndexed { index, func ->
            val methodName = func.simpleName.asString()
            actionMapCode.add("%S to { actionParams, context ->\n", methodName.lowercase())
            actionMapCode.indent()
            actionMapCode.add("runCatching {\n")
            actionMapCode.indent()

            val argExprs = mutableListOf<CodeBlock>()
            func.parameters.forEach { param ->
                val paramName = param.name?.asString() ?: ""
                val paramType = param.type.resolve().toTypeName()
                val isNullable = param.type.resolve().isMarkedNullable
                val hasDefault = param.hasDefault

                when (paramType) {
                    CN_PLUGIN_FILESYSTEM -> argExprs.add(CodeBlock.of("context.fileSystem"))
                    org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_HOST_FILESYSTEM -> argExprs.add(CodeBlock.of("context.hostFileSystem"))
                    org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_EXECUTION_FILESYSTEM -> argExprs.add(CodeBlock.of("context.executionFileSystem"))
                    CN_PLUGIN_LOGGER -> argExprs.add(CodeBlock.of("context.logger"))
                    CN_PROGRESS_REPORTER -> argExprs.add(CodeBlock.of("context.progress"))
                    CN_PLUGIN_CONTEXT -> argExprs.add(CodeBlock.of("context"))
                    else -> {
                        if (isNullable) {
                            argExprs.add(
                                CodeBlock.of(
                                    "actionParams[%S]?.let { %T.%M<%T>(it) }",
                                    paramName,
                                    CN_JSON,
                                    MN_DECODE_FROM_JSON_ELEMENT,
                                    paramType
                                )
                            )
                        } else if (hasDefault) {
                            argExprs.add(
                                CodeBlock.of(
                                    "actionParams[%S]?.let { %T.%M<%T>(it) }",
                                    paramName,
                                    CN_JSON,
                                    MN_DECODE_FROM_JSON_ELEMENT,
                                    paramType.copy(nullable = false)
                                )
                            )
                        } else {
                            argExprs.add(
                                CodeBlock.of(
                                    "actionParams[%S]?.let { %T.%M<%T>(it) } ?: throw %T(%S)",
                                    paramName,
                                    CN_JSON,
                                    MN_DECODE_FROM_JSON_ELEMENT,
                                    paramType,
                                    CN_ILLEGAL_ARGUMENT_EXCEPTION,
                                    "Missing required action parameter: $paramName"
                                )
                            )
                        }
                    }
                }
            }

            val argsBlock = CodeBlock.builder()
            argExprs.forEachIndexed { aIdx, expr ->
                argsBlock.add(expr)
                if (aIdx < argExprs.size - 1) argsBlock.add(", ")
            }

            actionMapCode.add("processor.%L(%L)\n", methodName, argsBlock.build())
            actionMapCode.unindent()
            actionMapCode.add("}\n")
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
            .addParameter("parameters", paramsMapType)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(CN_RESULT.parameterizedBy(Unit::class.asClassName()))
            .addStatement(
                "val handler = actionHandlers[action.functionName.lowercase()] ?: return %T.failure(%T(\"Unknown action: \${action.functionName}\"))",
                CN_RESULT,
                CN_ILLEGAL_ARGUMENT_EXCEPTION
            )
            .addStatement("return handler(parameters, context)")

        dispatcherType.addFunction(runActionFunc.build())

        val checkLocksFunction = classDeclaration.getAllFunctions()
            .find { func -> func.annotations.any { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_LOCKS_ANNOTATION) } }

        val checkLocksFunc = FunSpec.builder("refreshLocks")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(CN_MAP.parameterizedBy(String::class.asClassName(), Boolean::class.asClassName()))

        if (checkLocksFunction != null) {
            checkLocksFunc.addStatement("return processor.%L(context)", checkLocksFunction.simpleName.asString())
        } else {
            checkLocksFunc.addStatement("return emptyMap()")
        }

        dispatcherType.addFunction(checkLocksFunc.build())

        return dispatcherType.build()
    }
}
