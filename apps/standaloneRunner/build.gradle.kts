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
            api(project(":plugin-api"))
            implementation(project(":shared:core"))
            implementation(project(":shared:logic"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.slf4j.simple)
            implementation(libs.kermit)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)

            // Dynamic target plugin support: defaults to completeExample, customizable via -PtargetPlugin=:plugins:myPlugin or by invoking plugin tasks
            val invokedPluginTask = gradle.startParameter.taskNames.firstOrNull { it.startsWith(":plugins:") }
            val detectedPlugin = invokedPluginTask?.let { taskName ->
                val parts = taskName.split(":")
                if (parts.size >= 3) ":${parts[1]}:${parts[2]}" else null
            }
            val targetPlugin = findProperty("targetPlugin")?.toString() ?: detectedPlugin ?: ":plugins:completeExample"
            implementation(project(targetPlugin))
        }

        jvmTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.kotlin.test)
        }
    }
}

val targetPluginShortName = (findProperty("targetPlugin")?.toString()
    ?: gradle.startParameter.taskNames.firstOrNull { it.startsWith(":plugins:") }?.let { taskName ->
        val parts = taskName.split(":")
        if (parts.size >= 3) parts[2] else null
    }
    ?: "completeExample").substringAfterLast(":")

val customAppName = findProperty("appName")?.toString()
val appName = customAppName ?: when (targetPluginShortName) {
    "completeExample" -> "CompleteExample"
    "minimalExample" -> "MinimalExample"
    else -> targetPluginShortName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

buildkonfig {
    packageName = "org.wip.plugintoolkit.standalone"
    objectName = "StandaloneAppConfig"

    defaultConfigs {
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            "VERSION",
            libs.versions.app.get()
        )
    }
}

compose.desktop {
    application {
        mainClass = "org.wip.plugintoolkit.standalone.StandaloneMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe, TargetFormat.AppImage)
            packageName = appName
            packageVersion = libs.versions.app.get()
            vendor = "Wip-Sama"
            description = "$appName Standalone Application"
            includeAllModules = true

            windows {
                upgradeUuid = "a6873dc2-4e4b-4c07-b2e1-872f9011de9a"
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

        val pluginJarProp = findProperty("pluginJar")?.toString()
        val extraJvmArgs = mutableListOf("-Dcompose.desktop.verbose=true", "-Xmx2G")
        if (!pluginJarProp.isNullOrBlank()) {
            extraJvmArgs.add("-Dplugin.jar=$pluginJarProp")
        }
        jvmArgs(*extraJvmArgs.toTypedArray())

        buildTypes.release.proguard {
            obfuscate.set(false)
            configurationFiles.from(project.file("../desktopApp/compose-desktop.pro"))
        }
    }
}

val packagePortableZip by tasks.registering(Zip::class) {
    group = "compose desktop"
    description = "Packages a portable zip distribution containing the standalone plugin application and a .portable marker file."

    val createDistributableTask = tasks.matching {
        it.name == "createReleaseDistributable" || it.name == "createDistributable" || it.name == "packageAppImage" || it.name == "packageReleaseAppImage"
    }
    dependsOn(createDistributableTask)

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
