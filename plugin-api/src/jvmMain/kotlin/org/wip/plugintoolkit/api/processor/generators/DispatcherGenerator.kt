package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_COROUTINE_SCOPE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_DATA_PROCESSOR
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_DISPATCHERS
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_EXECUTION_RESULT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_ILLEGAL_ARGUMENT_EXCEPTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_ILLEGAL_STATE_EXCEPTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_JOB_HANDLE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_LIST
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_MAP
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_ACTION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_CONTEXT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_REQUEST
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_RESPONSE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_SIGNAL
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_RESULT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_ASYNC
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_CANCEL
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_DECODE_FROM_JSON_ELEMENT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_ENCODE_FROM_JSON_ELEMENT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_LAUNCH
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_SIGNAL_CANCEL
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_SIGNAL_PAUSE
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_SUPERVISOR_JOB
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_JSON
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_LOGGER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PROGRESS_REPORTER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_FILESYSTEM
import org.wip.plugintoolkit.api.processor.ProcessorConstants.RESUME_STATE_ANNOTATION

object DispatcherGenerator {
    fun generateDispatcherClass(
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
                    paramType == CN_PLUGIN_CONTEXT -> {
                        mapCode.add("context")
                    }
                    param.annotations.any { it.hasQualifiedName(RESUME_STATE_ANNOTATION) } -> {
                        mapCode.add("request.resumeState?.let { %T.%M<%T>(it) }", CN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType)
                    }
                    hasDefault -> {
                        mapCode.add("if (request.parameters.containsKey(%S)) %T.%M<%T>(request.parameters[%S]!!) else null", paramName, CN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType.copy(nullable = false), paramName)
                    }
                    isNullable -> {
                        mapCode.add("request.parameters[%S]?.let { %T.%M<%T>(it) }", paramName, CN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType)
                    }
                    else -> {
                        mapCode.add("%T.%M<%T>(request.parameters[%S] ?: throw %T(%S))", CN_JSON, MN_DECODE_FROM_JSON_ELEMENT, paramType, paramName, CN_ILLEGAL_ARGUMENT_EXCEPTION, "Missing mandatory parameter: $paramName")
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
                mapCode.add("%T.Success(%T(result = %T.%M(result), metadata = mapOf(\"status\" to \"success\")))\n", CN_EXECUTION_RESULT, CN_PLUGIN_RESPONSE, CN_JSON, MN_ENCODE_FROM_JSON_ELEMENT)
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
            .addStatement("val handler = handlers[request.method.lowercase()] ?: throw %T(%S)", CN_ILLEGAL_ARGUMENT_EXCEPTION, "Unknown method: \${request.method}")
            .addStatement("handler(request, context)")
            .nextControlFlow("catch (e: Exception)")
            .addStatement("%T.Error(e.message ?: \"Unknown error\", e)", CN_EXECUTION_RESULT)
            .endControlFlow()
        
        dispatcherType.addFunction(processFunc.build())

        val processAsyncFunc = FunSpec.builder("processAsync")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("request", CN_PLUGIN_REQUEST)
            .addParameter("context", CN_PLUGIN_CONTEXT)
            .returns(CN_JOB_HANDLE)
            .addCode(CodeBlock.builder()
                .addStatement("val handler = handlers[request.method.lowercase()] ?: throw %T(%S)", CN_ILLEGAL_ARGUMENT_EXCEPTION, "Unknown method: \${request.method}")
                .addStatement("val scope = %T(%T.Default + %M())", CN_COROUTINE_SCOPE, CN_DISPATCHERS, MN_SUPERVISOR_JOB)
                .beginControlFlow("val deferred = scope.%M", MN_ASYNC)
                .beginControlFlow("try")
                .addStatement("handler(request, context)")
                .nextControlFlow("catch (e: Exception)")
                .addStatement("%T.Error(e.message ?: \"Unknown error\", e)", CN_EXECUTION_RESULT)
                .endControlFlow()
                .endControlFlow()
                .add("\n")
                .beginControlFlow("return object : %T", CN_JOB_HANDLE)
                .addStatement("override val result = deferred")
                .beginControlFlow("override fun pause()")
                .addStatement("scope.%M { context.signals.sendSignal(%T.%M) }", MN_LAUNCH, CN_PLUGIN_SIGNAL, MN_SIGNAL_PAUSE)
                .endControlFlow()
                .beginControlFlow("override fun cancel(force: Boolean)")
                .addStatement("scope.%M { context.signals.sendSignal(%T.%M) }", MN_LAUNCH, CN_PLUGIN_SIGNAL, MN_SIGNAL_CANCEL)
                .addStatement("deferred.cancel()")
                .beginControlFlow("if (force)")
                .addStatement("scope.%M()", MN_CANCEL)
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .build())
        
        dispatcherType.addFunction(processAsyncFunc.build())

        // RunAction
        val actionMapType = CN_MAP.parameterizedBy(
            String::class.asClassName(),
            LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(CN_PLUGIN_CONTEXT)),
                returnType = CN_RESULT.parameterizedBy(Unit::class.asClassName())
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
                val paramType = param.type.resolve().toTypeName()
                when (paramType) {
                    CN_PLUGIN_FILESYSTEM -> "context.fileSystem"
                    CN_PLUGIN_LOGGER -> "context.logger"
                    CN_PROGRESS_REPORTER -> "context.progress"
                    CN_PLUGIN_CONTEXT -> "context"
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
            .returns(CN_RESULT.parameterizedBy(Unit::class.asClassName()))
            .addStatement("val handler = actionHandlers[action.functionName.lowercase()] ?: return %T.failure(%T(\"Unknown action: \${action.functionName}\"))", CN_RESULT, CN_ILLEGAL_ARGUMENT_EXCEPTION)
            .addStatement("return handler(context)")
        
        dispatcherType.addFunction(runActionFunc.build())

        return dispatcherType.build()
    }
}
