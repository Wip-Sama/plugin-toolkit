import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover)
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
            verify {
                rule {
                    minBound(50) // will increase in the future
                }
            }
        }
    }
}

// Aggregate coverage from all subprojects into the root report
dependencies {
    kover(project(":shared:core"))
    kover(project(":shared:logic"))
    kover(project(":shared:gui"))
    kover(project(":plugin-api"))
    kover(project(":apps:cliApp"))
    kover(project(":plugins:completeExample"))
    kover(project(":plugins:minimalExample"))
}

abstract class BuildLocalPluginRepoTask : DefaultTask() {
    @get:InputFile
    abstract val minimalJar: RegularFileProperty

    @get:InputFile
    abstract val completeJar: RegularFileProperty

    @get:InputFile
    @get:Optional
    abstract val completeChangelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val repoRoot = outputDir.get().asFile
        if (repoRoot.exists()) {
            repoRoot.deleteRecursively()
        }
        val pluginsRoot = File(repoRoot, "plugins").apply { mkdirs() }
        File(repoRoot, "flows").mkdirs()

        val pluginSpecs = listOf(
            Triple(minimalJar.get().asFile, "minimalExample", null),
            Triple(completeJar.get().asFile, "completeExample", completeChangelog.orNull?.asFile)
        )

        val slurper = JsonSlurper()
        val pluginEntries = pluginSpecs.map { (jarFile, baseName, changelogFile) ->
            val manifestText = readManifestFromJar(jarFile)
            @Suppress("UNCHECKED_CAST")
            val manifest = slurper.parseText(manifestText) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val pluginMeta = manifest["plugin"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val requirements = manifest["requirements"] as? Map<String, Any?>

            val pkg = pluginMeta["id"].toString()
            val name = pluginMeta["name"].toString()
            val version = pluginMeta["version"].toString()
            val description = pluginMeta["description"]?.toString()
            val targetAppVersion = requirements?.get("targetAppVersion")?.toString()

            val targetDir = File(pluginsRoot, pkg).apply { mkdirs() }
            val destFileName = "$baseName-$version.jar"
            val destJar = File(targetDir, destFileName)
            jarFile.copyTo(destJar, overwrite = true)

            File(targetDir, "manifest.json").writeText(manifestText, Charsets.UTF_8)
            if (changelogFile != null && changelogFile.exists()) {
                changelogFile.copyTo(File(targetDir, "changelog.md"), overwrite = true)
            }

            linkedMapOf(
                "name" to name,
                "fileName" to destFileName,
                "description" to description,
                "pkg" to pkg,
                "version" to version,
                "minAppVersion" to targetAppVersion,
                "size" to destJar.length(),
                "hash" to computeSha256(destJar)
            )
        }

        val indexFile = File(repoRoot, "index.json")
        val indexPayload = linkedMapOf(
            "name" to "Local Dev Examples Repository",
            "url" to indexFile.absolutePath.replace('\\', '/'),
            "schemaVersion" to 1,
            "pluginsFolder" to "plugins",
            "flowsFolder" to "flows",
            "plugins" to pluginEntries,
            "flows" to emptyList<Any>()
        )
        indexFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(indexPayload)) + "\n", Charsets.UTF_8)
        logger.lifecycle("Generated local plugin repository at: ${repoRoot.absolutePath}")
    }

    private fun readManifestFromJar(jarFile: File): String {
        ZipFile(jarFile).use { zip ->
            val entry = zip.getEntry("META-INF/manifest.json")
                ?: zip.getEntry("manifest.json")
                ?: error("No manifest.json found inside ${jarFile.absolutePath}")
            return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

evaluationDependsOn(":plugins:minimalExample")
evaluationDependsOn(":plugins:completeExample")

tasks.register<BuildLocalPluginRepoTask>("buildLocalPluginRepo") {
    group = "plugin development"
    description = "Builds minimalExample and completeExample JARs and packages them into a local plugin repository under build/local-plugin-repo."
    dependsOn(":plugins:minimalExample:jar", ":plugins:completeExample:jar")

    minimalJar.set(project(":plugins:minimalExample").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    completeJar.set(project(":plugins:completeExample").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    val changelogFile = file("plugins/completeExample/changelog.md")
    if (changelogFile.exists()) {
        completeChangelog.set(changelogFile)
    }
    outputDir.set(layout.buildDirectory.dir("local-plugin-repo"))
}
