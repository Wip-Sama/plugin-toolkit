package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_JSON
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_MAP
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_ENTRY
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CN_PLUGIN_MODULE_PROVIDER
import org.wip.plugintoolkit.api.processor.ProcessorConstants.MN_DECODE_FROM_JSON_ELEMENT
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETTING_ANNOTATION

object ModuleGenerator {
    fun generateModuleProvider(
        providerName: String,
        packageName: String,
        baseClassName: String,
        dispatcherName: String,
        entryName: String,
        settingsClasses: List<KSClassDeclaration>
    ): TypeSpec {
        val jsonElementCN = ClassName("kotlinx.serialization.json", "JsonElement")
        val settingsMapType = CN_MAP.parameterizedBy(String::class.asClassName(), jsonElementCN)
        
        val getModuleFunc = FunSpec.builder("getKoinModule")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("settings", settingsMapType)
            .returns(ClassName("org.koin.core.module", "Module"))
            .addCode("return org.koin.dsl.module {\n")
        
        settingsClasses.forEach { cls ->
            val clsName = cls.toClassName()
            getModuleFunc.addCode("  single {\n")
            getModuleFunc.addCode("    %T(\n", clsName)
            
            val props = cls.getAllProperties().filter { p -> 
                p.annotations.any { a -> a.hasQualifiedName(PLUGIN_SETTING_ANNOTATION) }
            }.toList()
            
            props.forEachIndexed { index, prop ->
                val name = prop.simpleName.asString()
                val type = prop.type.resolve().toTypeName()
                
                val defaultValue = when (type) {
                    String::class.asClassName() -> CodeBlock.of("%S", "")
                    Int::class.asClassName() -> CodeBlock.of("0")
                    Boolean::class.asClassName() -> CodeBlock.of("false")
                    Double::class.asClassName() -> CodeBlock.of("0.0")
                    else -> CodeBlock.of("%T()", type)
                }

                getModuleFunc.addCode(
                    "      settings[%S]?.let { %T.%M<%T>(it) } ?: %L",
                    name, CN_JSON, MN_DECODE_FROM_JSON_ELEMENT, type, defaultValue
                )
                if (index < props.size - 1) getModuleFunc.addCode(", ")
                getModuleFunc.addCode("\n")
            }
            
            getModuleFunc.addCode("    )\n")
            getModuleFunc.addCode("  }\n")
        }
        
        getModuleFunc.addStatement("  single { %T(get()) }", ClassName(packageName, baseClassName))
        getModuleFunc.addStatement("  single<%T> { %T(get()) }", CN_PLUGIN_ENTRY, ClassName(packageName, entryName))
        
        getModuleFunc.addCode("}\n")

        return TypeSpec.classBuilder(providerName)
            .addSuperinterface(CN_PLUGIN_MODULE_PROVIDER)
            .addFunction(getModuleFunc.build())
            .build()
    }
}
