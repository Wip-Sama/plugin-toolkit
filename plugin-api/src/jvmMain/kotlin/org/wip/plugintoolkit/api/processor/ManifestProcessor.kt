package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSClassifierReference
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import org.wip.plugintoolkit.api.Changelog
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_ACTION_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_INFO_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETTING_ANNOTATION
import java.io.File
import org.wip.plugintoolkit.api.annotations.PluginInfo as PluginInfoAnnotation

class ManifestProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ManifestProcessor(environment.codeGenerator, environment.logger)
    }
}

class ManifestProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val grammarRegex = Regex("^(?:([a-zA-Z0-9_-]+)/)?([a-zA-Z0-9_-]+)(?::([a-zA-Z0-9_*-]+))?$")

    private fun validateSemanticTypes(semanticTypes: List<String>?, symbol: com.google.devtools.ksp.symbol.KSNode) {
        if (semanticTypes.isNullOrEmpty()) return
        for (rawType in semanticTypes) {
            val tokens = rawType.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            for (token in tokens) {
                if (!grammarRegex.matches(token)) {
                    logger.warn(
                        "Semantic type '$token' does not follow the standard [namespace/][name][:variant] grammar",
                        symbol
                    )
                }
            }
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(PluginInfoAnnotation::class.qualifiedName!!)

        symbols.filterIsInstance<KSClassDeclaration>().forEach { classDeclaration ->
            generatePluginMetadata(classDeclaration, resolver)
        }

        return emptyList()
    }

    private fun generatePluginMetadata(classDeclaration: KSClassDeclaration, resolver: Resolver) {
        val pluginInfoAnnotation = classDeclaration.annotations.find {
            it.hasQualifiedName(PLUGIN_INFO_ANNOTATION)
        } ?: return

        val id = pluginInfoAnnotation.arguments.find { it.name?.asString() == "id" }?.value as String
        val name = pluginInfoAnnotation.arguments.find { it.name?.asString() == "name" }?.value as String
        val version = pluginInfoAnnotation.arguments.find { it.name?.asString() == "version" }?.value as String
        val description = pluginInfoAnnotation.arguments.find { it.name?.asString() == "description" }?.value as String
        val minMemoryMb = pluginInfoAnnotation.arguments.find { it.name?.asString() == "minMemoryMb" }?.value as Int
        val minExecutionTimeMs =
            pluginInfoAnnotation.arguments.find { it.name?.asString() == "minExecutionTimeMs" }?.value as Int
        val supportedOs =
            (pluginInfoAnnotation.arguments.find { it.name?.asString() == "supportedOs" }?.value as? List<*>)
                ?.mapNotNull { item ->
                    val valStr = when (item) {
                        is KSType -> item.declaration.simpleName.asString()
                        is KSClassifierReference -> item.referencedName()
                        is KSDeclaration -> item.simpleName.asString()
                        else -> item?.toString() ?: ""
                    }
                    try {
                        OS.valueOf(valStr.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

        val packageName = classDeclaration.packageName.asString()
        val baseClassName = classDeclaration.simpleName.asString()

        // Find all classes in this module that have @PluginSetting annotations
        val settingsClasses = resolver.getSymbolsWithAnnotation(PLUGIN_SETTING_ANNOTATION)
            .mapNotNull {
                var current = it.parent
                while (current != null && current !is KSClassDeclaration) {
                    current = current.parent
                }
                current as? KSClassDeclaration
            }
            .distinct()
            .toList()

        val functions = classDeclaration.getAllFunctions().filter {
            it.annotations.any { ann -> ann.hasQualifiedName(CAPABILITY_ANNOTATION) }
        }.toList()

        // Validate semantic types on functions
        functions.forEach { func ->
            func.parameters.forEach { param ->
                val paramAnn =
                    param.annotations.find { it.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.CAPABILITY_PARAM_ANNOTATION) }
                val sem =
                    (paramAnn?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                validateSemanticTypes(sem, param)
            }

            // Validate outputs
            val returnTypeKS = func.returnType?.resolve()
            if (returnTypeKS != null && returnTypeKS.toTypeName().toString() != "kotlin.Unit") {
                val funcOutputAnn = func.annotations.find {
                    it.hasQualifiedName("org.wip.plugintoolkit.api.annotations.CapabilityOutput")
                }
                if (funcOutputAnn != null) {
                    val sem =
                        (funcOutputAnn.arguments.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                    validateSemanticTypes(sem, func)
                } else {
                    val declaration = returnTypeKS.declaration
                    val isDataClass =
                        declaration is KSClassDeclaration && declaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.DATA)
                    if (isDataClass) {
                        val classDecl = declaration as KSClassDeclaration
                        classDecl.getAllProperties().forEach { prop ->
                            val propAnn = prop.annotations.find {
                                it.hasQualifiedName("org.wip.plugintoolkit.api.annotations.CapabilityOutput")
                            }
                            val sem =
                                (propAnn?.arguments?.find { it.name?.asString() == "semanticTypes" }?.value as? List<*>)?.filterIsInstance<String>()
                            validateSemanticTypes(sem, prop)
                        }
                    }
                }
            }
        }

        val actions = classDeclaration.getAllFunctions().filter {
            it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_ACTION_ANNOTATION) }
        }.toList()

        // 1. Parse Changelog
        var changelogObj: Changelog? = null
        val sourceFile = classDeclaration.containingFile
        val changelogFile = sourceFile?.let { findChangelogFile(it) }

        if (changelogFile != null) {
            val content = changelogFile.readText()
            changelogObj = org.wip.plugintoolkit.api.utils.ChangelogParser.parse(content)

            // Bundle changelog into resources
            val extension = changelogFile.extension
            writeFile(sourceFile, "", "resources/changelog", extension, content)
        }

        // 2. Generate Kotlin Classes
        val fileSpec = KotlinGenerator.generate(
            packageName, baseClassName, id, name, version, description,
            minMemoryMb, minExecutionTimeMs, supportedOs, functions, settingsClasses, actions, classDeclaration
        )

        val generatedFileName = baseClassName + "Generated"
        val file = codeGenerator.createNewFile(
            Dependencies(true, classDeclaration.containingFile!!),
            packageName,
            generatedFileName
        )
        file.writer().use {
            fileSpec.writeTo(it)
        }

        // 3. Generate SPI Service File
        val providerName = baseClassName + "ModuleProvider"
        writeFile(
            sourceFile,
            "",
            "META-INF/services/${org.wip.plugintoolkit.api.PluginModuleProvider::class.qualifiedName}",
            "",
            "$packageName.$providerName"
        )

        // 4. Check for migrations.json
        val migrationsFile = sourceFile?.let { findMigrationsFile(it) }
        var hasMigrations = false
        if (migrationsFile != null && migrationsFile.exists()) {
            hasMigrations = true
            writeFile(sourceFile, "", "META-INF/migrations", "json", migrationsFile.readText())
        }

        // 5. Generate manifest.json
        val allSettingsProperties = settingsClasses.flatMap {
            it.getAllProperties()
                .filter { p -> p.annotations.any { a -> a.hasQualifiedName(org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETTING_ANNOTATION) } }
        }.toList()
        val manifestJson = ManifestJsonGenerator.generate(
            classDeclaration,
            id,
            name,
            version,
            description,
            minMemoryMb,
            minExecutionTimeMs,
            supportedOs,
            functions,
            allSettingsProperties,
            actions,
            changelogObj,
            hasMigrations
        )
        writeFile(sourceFile, "", "META-INF/manifest", "json", manifestJson)
    }

    private fun writeFile(
        sourceFile: KSFile?,
        packageName: String,
        fileName: String,
        extension: String,
        content: String
    ) {
        val file = codeGenerator.createNewFile(
            Dependencies(true, sourceFile!!),
            packageName,
            fileName,
            extension
        )
        file.writer().use { it.write(content) }
    }

    private fun findChangelogFile(sourceFile: KSFile): File? {
        var currentDir = File(sourceFile.filePath).parentFile
        while (currentDir != null) {
            val changelogMd = File(currentDir, "changelog.md")
            if (changelogMd.exists()) return changelogMd

            val resourcesChangelogMd = File(currentDir, "src/main/resources/changelog.md")
            if (resourcesChangelogMd.exists()) return resourcesChangelogMd

            if (File(currentDir, "build.gradle.kts").exists() || File(currentDir, "build.gradle").exists()) {
                val modResourcesMd = File(currentDir, "src/main/resources/changelog.md")
                if (modResourcesMd.exists()) return modResourcesMd
                return if (changelogMd.exists()) changelogMd else null
            }
            currentDir = currentDir.parentFile
        }
        return null
    }

    private fun findMigrationsFile(sourceFile: KSFile): File? {
        var currentDir = File(sourceFile.filePath).parentFile
        while (currentDir != null) {
            val migrationsJson = File(currentDir, "migrations.json")
            if (migrationsJson.exists()) return migrationsJson

            val resourcesMigrationsJson = File(currentDir, "src/main/resources/migrations.json")
            if (resourcesMigrationsJson.exists()) return resourcesMigrationsJson

            if (File(currentDir, "build.gradle.kts").exists() || File(currentDir, "build.gradle").exists()) {
                val modResourcesJson = File(currentDir, "src/main/resources/migrations.json")
                if (modResourcesJson.exists()) return modResourcesJson
                return if (migrationsJson.exists()) migrationsJson else null
            }
            currentDir = currentDir.parentFile
        }
        return null
    }
}
