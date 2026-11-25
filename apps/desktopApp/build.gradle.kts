import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            api(project(":shared:gui"))
            implementation(project(":shared:core"))
            implementation(project(":shared:logic"))
            implementation(project(":plugin-api"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.slf4j.simple)
            implementation(libs.kermit)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.io.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
        }

        jvmTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

    }
}

buildkonfig {
    packageName = "org.wip.plugintoolkit"
    objectName = "AppConfig"

    defaultConfigs {
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            "VERSION",
            libs.versions.app.get()
        )
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            "MIN_COMPATIBLE_PLUGIN_VERSION",
            libs.versions.min.compatible.plugin.version.get()
        )
    }
}

compose.desktop {
    application {
        mainClass = "org.wip.plugintoolkit.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe, TargetFormat.AppImage)
            packageName = "PluginToolkit"
            packageVersion = libs.versions.app.get().substringBefore("-")
            vendor = "Wip-Sama"
            description = "A working toolkit for using plugins."
            includeAllModules = true

            windows {
                upgradeUuid = "8ddd074e-db0a-4ae3-ba98-35013c6ae5cc"
                iconFile.set(project.file("../../shared/gui/src/commonMain/composeResources/files/app_logo.ico"))
            }
            macOS {
                iconFile.set(project.file("../../shared/gui/src/commonMain/composeResources/drawable/icon.icns"))
            }
            linux {
                iconFile.set(project.file("../../shared/gui/src/commonMain/composeResources/drawable/icon.png"))
            }
            modules(
                "java.instrument",
                "jdk.unsupported",
                "java.naming",
                "java.sql",
                "java.management",
                "jdk.crypto.ec",
                "java.desktop",
                "java.xml",
                "java.scripting",
                "java.logging",
                "jdk.charsets"
            )
        }

        jvmArgs("-Dcompose.desktop.verbose=true", "-Xmx2G")

        buildTypes.release.proguard {
            obfuscate.set(false)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}

val packagePortableZip by tasks.registering(Zip::class) {
    group = "compose desktop"
    description = "Packages a portable zip distribution containing the application and a .portable marker file."

    val createDistributableTask = tasks.matching { 
        it.name == "createReleaseDistributable" || it.name == "createDistributable" || it.name == "packageAppImage" || it.name == "packageReleaseAppImage"
    }
    dependsOn(createDistributableTask)


    val appName = "PluginToolkit"
    val osName = System.getProperty("os.name", "").lowercase()
    val platformName = when {
        osName.contains("win") -> "windows"
        osName.contains("mac") -> "macos"
        else -> "linux"
    }

    archiveBaseName.set("$appName-$platformName-portable")
    archiveVersion.set(libs.versions.app.get())
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(layout.buildDirectory.dir("compose/binaries/main/app/$appName")) {
        into(appName)
    }
    from(layout.buildDirectory.dir("compose/binaries/main-release/app/$appName")) {
        into(appName)
    }

    val markerDir = layout.buildDirectory.dir("tmp/portable-marker")
    doFirst {
        val markerFile = markerDir.get().asFile.resolve(".portable")
        markerFile.parentFile.mkdirs()
        markerFile.writeText("portable=true\n")
    }
    from(markerDir) {
        into(appName)
        include(".portable")
    }
}

abstract class PreparePortableSandboxTask : DefaultTask() {
    @get:Input
    abstract val cleanData: Property<Boolean>

    @get:OutputDirectory
    abstract val sandboxDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val dir = sandboxDir.get().asFile
        if (cleanData.get()) {
            val dataDir = File(dir, "data")
            if (dataDir.exists()) {
                dataDir.deleteRecursively()
                logger.lifecycle("Cleaned portable sandbox data directory: ${dataDir.absolutePath}")
            }
        }
        dir.mkdirs()
        File(dir, ".portable").writeText("portable=true\n", Charsets.UTF_8)
    }
}

class PortableDevJvmArgsProvider(
    private val sandboxDir: Provider<Directory>,
    private val loadDevExamples: Provider<Boolean>,
    private val minimalJar: Provider<RegularFile>,
    private val completeJar: Provider<RegularFile>,
    private val localRepoIndex: Provider<RegularFile>
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val args = mutableListOf(
            "-Dplugintoolkit.portable=true",
            "-Dplugintoolkit.portable.dir=${sandboxDir.get().asFile.absolutePath}"
        )
        if (loadDevExamples.get()) {
            val pluginsValue = listOf(
                minimalJar.get().asFile.absolutePath,
                completeJar.get().asFile.absolutePath
            ).joinToString(File.pathSeparator)
            args.add("-Dplugintoolkit.dev.plugins=$pluginsValue")
            args.add("-Dplugintoolkit.dev.repo=${localRepoIndex.get().asFile.absolutePath}")
        }
        return args
    }
}

evaluationDependsOn(":plugins:minimalExample")
evaluationDependsOn(":plugins:completeExample")

val portableSandboxDir = layout.buildDirectory.dir("portable-sandbox")
val cleanPortableProp = providers.gradleProperty("cleanPortable").map { it.toBoolean() }.orElse(false)
val devExamplesProp = providers.gradleProperty("devExamples").map { it.toBoolean() }.orElse(true)
val portableProp = providers.gradleProperty("portable").map { it.toBoolean() }.orElse(false)

val minimalExampleJar = project(":plugins:minimalExample").tasks.named<Jar>("jar").flatMap { it.archiveFile }
val completeExampleJar = project(":plugins:completeExample").tasks.named<Jar>("jar").flatMap { it.archiveFile }
val localRepoIndexFile = rootProject.layout.buildDirectory.file("local-plugin-repo/index.json")

val preparePortableSandbox = tasks.register<PreparePortableSandboxTask>("preparePortableSandbox") {
    group = "application"
    description = "Prepares an isolated portable sandbox directory under build/portable-sandbox and builds dev plugins/local repo."
    cleanData.set(cleanPortableProp)
    sandboxDir.set(portableSandboxDir)
    if (devExamplesProp.get()) {
        dependsOn(":plugins:minimalExample:jar", ":plugins:completeExample:jar", ":buildLocalPluginRepo")
    }
}

val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")

tasks.register<JavaExec>("runPortable") {
    group = "application"
    description = "Runs PluginToolkit in an isolated portable sandbox (build/portable-sandbox), auto-loading minimalExample, completeExample, and the local repository."
    dependsOn(preparePortableSandbox)
    mainClass.set("org.wip.plugintoolkit.MainKt")
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    workingDir(portableSandboxDir.map { it.asFile })
    jvmArgs("-Dcompose.desktop.verbose=true", "-Xmx2G")
    jvmArgumentProviders.add(
        PortableDevJvmArgsProvider(
            sandboxDir = portableSandboxDir,
            loadDevExamples = devExamplesProp,
            minimalJar = minimalExampleJar,
            completeJar = completeExampleJar,
            localRepoIndex = localRepoIndexFile
        )
    )
}

if (portableProp.get()) {
    tasks.withType<JavaExec>().configureEach {
        if (name == "run" || name.startsWith("hotRun")) {
            dependsOn(preparePortableSandbox)
            jvmArgumentProviders.add(
                PortableDevJvmArgsProvider(
                    sandboxDir = portableSandboxDir,
                    loadDevExamples = devExamplesProp,
                    minimalJar = minimalExampleJar,
                    completeJar = completeExampleJar,
                    localRepoIndex = localRepoIndexFile
                )
            )
        }
    }
}
