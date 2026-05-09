import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.nav3)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kermit)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.filekit.coil)
            implementation(libs.kotlinx.io.core)
            implementation(project(":plugin-api"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.dbus.java.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.slf4j.simple)
            implementation(libs.kotlinx.datetime)
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
    }
}


compose.desktop {
    application {
        mainClass = "org.wip.plugintoolkit.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "PluginToolkit"
            packageVersion = libs.versions.app.get()
            vendor = "Wip-Sama"
            description = "A working toolkit for using plugins."
            includeAllModules = true

            windows {
                upgradeUuid = "8ddd074e-db0a-4ae3-ba98-35013c6ae5cc"
            }
            // Modules often required by JNA/DBus, reflection, and core features
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
