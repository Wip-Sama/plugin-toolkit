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
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
        }

        jvmTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.kotlin.test)
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
            "1.7.4"
        )
    }
}

compose.desktop {
    application {
        mainClass = "org.wip.plugintoolkit.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe, TargetFormat.AppImage)
            packageName = "PluginToolkit"
            packageVersion = libs.versions.app.get()
            vendor = "Wip-Sama"
            description = "A working toolkit for using plugins."
            includeAllModules = true

            windows {
                upgradeUuid = "8ddd074e-db0a-4ae3-ba98-35013c6ae5cc"
                iconFile.set(project.file("../shared/gui/src/commonMain/composeResources/files/app_logo.ico"))
            }
            macOS {
                iconFile.set(project.file("../shared/gui/src/commonMain/composeResources/drawable/icon.icns"))
            }
            linux {
                iconFile.set(project.file("../shared/gui/src/commonMain/composeResources/drawable/icon.png"))
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
        it.name == "createReleaseDistributable" || it.name == "createDistributable" 
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
