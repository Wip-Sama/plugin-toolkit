package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.Changelog
import org.wip.plugintoolkit.api.annotations.PluginInfo as PluginInfoAnnotation
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
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
        val minExecutionTimeMs = pluginInfoAnnotation.arguments.find { it.name?.asString() == "minExecutionTimeMs" }?.value as Int

        val functions = classDeclaration.getAllFunctions().filter { 
            it.annotations.any { ann -> ann.hasQualifiedName(CAPABILITY_ANNOTATION) }
        }.toList()

        val settingsProperties = classDeclaration.getAllProperties().filter { 
            it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_SETTING_ANNOTATION) }
        }.toList()

        val actions = classDeclaration.getAllFunctions().filter { 
            it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_ACTION_ANNOTATION) }
        }.toList()

        val packageName = classDeclaration.packageName.asString()
        val baseClassName = classDeclaration.simpleName.asString()

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
            minMemoryMb, minExecutionTimeMs, functions, settingsProperties, actions, classDeclaration
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
        val entryName = baseClassName + "PluginEntry"
        writeFile(sourceFile, "", "META-INF/services/${PluginEntry::class.qualifiedName}", "", "$packageName.$entryName")

        // 4. Generate manifest.json
        val manifestJson = ManifestJsonGenerator.generate(
            classDeclaration, id, name, version, description,
            minMemoryMb, minExecutionTimeMs, functions, settingsProperties, actions, changelogObj
        )
        writeFile(sourceFile, "", "META-INF/manifest", "json", manifestJson)
    }

    private fun writeFile(sourceFile: KSFile?, packageName: String, fileName: String, extension: String, content: String) {
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
            val changelogTxt = File(currentDir, "changelog.txt")
            if (changelogTxt.exists()) return changelogTxt
            val changelogMd = File(currentDir, "changelog.md")
            if (changelogMd.exists()) return changelogMd
            
            val resourcesChangelogTxt = File(currentDir, "src/main/resources/changelog.txt")
            if (resourcesChangelogTxt.exists()) return resourcesChangelogTxt
            val resourcesChangelogMd = File(currentDir, "src/main/resources/changelog.md")
            if (resourcesChangelogMd.exists()) return resourcesChangelogMd

            if (File(currentDir, "build.gradle.kts").exists() || File(currentDir, "build.gradle").exists()) {
                val modResourcesTxt = File(currentDir, "src/main/resources/changelog.txt")
                if (modResourcesTxt.exists()) return modResourcesTxt
                val modResourcesMd = File(currentDir, "src/main/resources/changelog.md")
                if (modResourcesMd.exists()) return modResourcesMd
                return if (changelogTxt.exists()) changelogTxt else if (changelogMd.exists()) changelogMd else null
            }
            currentDir = currentDir.parentFile
        }
        return null
    }
}
