package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_DATA_PROCESSOR
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_CONTEXT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_ENTRY
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_MANIFEST
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_FILESYSTEM
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_LOGGER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PROGRESS_REPORTER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_RESULT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_LOAD_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETUP_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_UPDATE_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_VALIDATE_ANNOTATION

object EntryGenerator {
    fun generateEntryClass(
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
            .returns(CN_RESULT.parameterizedBy(Unit::class.asClassName()))

        if (loadFunction != null) {
            val loadParams = loadFunction.parameters
            val callArgs = loadParams.joinToString(", ") { param ->
                val paramType = param.type.resolve().toTypeName()
                when (paramType) {
                    CN_PLUGIN_FILESYSTEM -> "context.fileSystem"
                    CN_PLUGIN_LOGGER -> "context.logger"
                    CN_PROGRESS_REPORTER -> "context.progress"
                    CN_PLUGIN_CONTEXT -> "context"
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
            .returns(CN_RESULT.parameterizedBy(Unit::class.asClassName()))

        if (setupFunction != null) {
            val setupParams = setupFunction.parameters
            val callArgs = setupParams.joinToString(", ") { param ->
                val paramType = param.type.resolve().toTypeName()
                when (paramType) {
                    CN_PLUGIN_FILESYSTEM -> "context.fileSystem"
                    CN_PLUGIN_LOGGER -> "context.logger"
                    CN_PROGRESS_REPORTER -> "context.progress"
                    CN_PLUGIN_CONTEXT -> "context"
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
            .returns(CN_RESULT.parameterizedBy(Unit::class.asClassName()))

        if (validateFunction != null) {
            val validateParams = validateFunction.parameters
            val callArgs = validateParams.joinToString(", ") { param ->
                val paramType = param.type.resolve().toTypeName()
                when (paramType) {
                    CN_PLUGIN_FILESYSTEM -> "context.fileSystem"
                    CN_PLUGIN_LOGGER -> "context.logger"
                    CN_PROGRESS_REPORTER -> "context.progress"
                    CN_PLUGIN_CONTEXT -> "context"
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
            .returns(CN_RESULT.parameterizedBy(Unit::class.asClassName()))

        if (updateFunction != null) {
            val updateParams = updateFunction.parameters
            val callArgs = updateParams.joinToString(", ") { param ->
                val paramType = param.type.resolve().toTypeName()
                when (paramType) {
                    CN_PLUGIN_FILESYSTEM -> "context.fileSystem"
                    CN_PLUGIN_LOGGER -> "context.logger"
                    CN_PROGRESS_REPORTER -> "context.progress"
                    CN_PLUGIN_CONTEXT -> "context"
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
}
