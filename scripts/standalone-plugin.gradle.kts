import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.zip.ZipFile
import java.util.jar.Manifest

@CacheableTask
abstract class MergeStandaloneServices : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArchives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun merge() {
        val outputRoot = outputDirectory.get().asFile
        outputRoot.deleteRecursively()
        val services = LinkedHashMap<String, LinkedHashSet<String>>()

        fun collect(path: String, text: String) {
            if (!path.startsWith("META-INF/services/")) return
            val lines = services.getOrPut(path) { LinkedHashSet() }
            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .forEach(lines::add)
        }

        inputArchives.files.forEach { input ->
            if (input.isDirectory) {
                input.walkTopDown().filter { it.isFile }.forEach { file ->
                    val path = file.relativeTo(input).invariantSeparatorsPath
                    if (path.startsWith("META-INF/services/")) collect(path, file.readText())
                }
            } else {
                ZipFile(input).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && entry.name.startsWith("META-INF/services/")) {
                            collect(entry.name, zip.getInputStream(entry).bufferedReader().use { it.readText() })
                        }
                    }
                }
            }
        }

        services.forEach { (path, providers) ->
            val output = outputRoot.resolve(path)
            output.parentFile.mkdirs()
            output.writeText(providers.joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

@CacheableTask
abstract class VerifyStandaloneJar : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun verify() {
        ZipFile(archiveFile.get().asFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            check("org/wip/plugintoolkit/api/standalone/StandalonePluginMainKt.class" in names) {
                "Standalone launcher is missing"
            }
            val manifestEntry = zip.getEntry("META-INF/MANIFEST.MF") ?: error("JAR manifest is missing")
            val manifest = zip.getInputStream(manifestEntry).use(::Manifest)
            check(
                manifest.mainAttributes.getValue("Main-Class") ==
                    "org.wip.plugintoolkit.api.standalone.StandalonePluginMainKt"
            ) { "Standalone JAR has an invalid Main-Class" }
            check(names.none {
                it.startsWith("org/wip/plugintoolkit/api/processor/") ||
                    it.startsWith("com/google/devtools/ksp/") ||
                    it.startsWith("com/squareup/kotlinpoet/")
            }) { "Standalone JAR contains build-time processor dependencies" }
            val servicePath = "META-INF/services/org.wip.plugintoolkit.api.PluginModuleProvider"
            val service = zip.getEntry(servicePath) ?: error("PluginModuleProvider service descriptor is missing")
            val providers = zip.getInputStream(service).bufferedReader().useLines { lines ->
                lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toList()
            }
            check(providers.isNotEmpty()) { "PluginModuleProvider service descriptor is empty" }
            check(providers.size == providers.distinct().size) { "PluginModuleProvider contains duplicate providers" }
        }
    }
}

val standaloneServicesDir = layout.buildDirectory.dir("generated/standalone-services")
val pluginJar = tasks.named<Jar>("jar")
val runtimeClasspath = configurations.getByName("runtimeClasspath")

val mergeStandaloneServices = tasks.register<MergeStandaloneServices>("mergeStandaloneServices") {
    dependsOn(pluginJar)
    inputArchives.from(pluginJar.flatMap { it.archiveFile }, runtimeClasspath)
    outputDirectory.set(standaloneServicesDir)
}

// Apply from a JVM plugin module after its dependencies have been declared.
val standaloneJar = tasks.register<Jar>("standaloneJar") {
    group = "distribution"
    description = "Builds an executable plugin JAR with its runtime dependencies."
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(pluginJar, mergeStandaloneServices)
    from({ zipTree(pluginJar.get().archiveFile.get().asFile) }) {
        exclude("META-INF/services/**")
    }
    from({
        runtimeClasspath.map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    }) {
        exclude("META-INF/services/**")
        exclude("META-INF/MANIFEST.MF", "module-info.class", "META-INF/versions/**/module-info.class")
        exclude("org/wip/plugintoolkit/api/processor/**")
    }
    from(standaloneServicesDir)
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest.attributes["Main-Class"] =
        "org.wip.plugintoolkit.api.standalone.StandalonePluginMainKt"
}

val verifyStandaloneJar = tasks.register<VerifyStandaloneJar>("verifyStandaloneJar") {
    dependsOn(standaloneJar)
    archiveFile.set(standaloneJar.flatMap { it.archiveFile })
}

tasks.named("check") {
    dependsOn(verifyStandaloneJar)
}
