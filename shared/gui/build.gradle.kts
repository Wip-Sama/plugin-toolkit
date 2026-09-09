plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:logic"))
            implementation(project(":plugin-api"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.nav3)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.filekit.dialogs)

            implementation(libs.filekit.dialogs.compose)
            implementation(libs.filekit.coil)
            implementation(libs.kermit)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
        }



        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
            implementation(libs.koin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.dbus.java.core)
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
        }
    }
}

compose.resources {
    packageOfResClass = "plugintoolkit.composeapp.generated.resources"
    publicResClass = true
}

// ── Native DLL build ───────────────────────────────────────────────────────────
// Builds toolkit-win-drag.dll from src/native/ via CMake and installs it into
// src/jvmMain/resources/windows/ for bundling in the JAR classpath.
//
// Run manually when the C++ source changes:
//   ./gradlew :shared:gui:buildNativeDll
//
// The pre-built DLL committed to resources is used for normal builds.
tasks.register("buildNativeDll") {
    group = "build"
    description = "Compiles toolkit-win-drag.dll via CMake (Windows only, requires CMake in PATH)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    val nativeSrcDir = layout.projectDirectory.dir("src/native").asFile
    val cmakeBuildDir = layout.buildDirectory.dir("native-cmake").get().asFile
    val resourceOutputDir = layout.projectDirectory.dir("src/jvmMain/resources/windows").asFile
    // Use the JDK Gradle is running on for JNI headers (handle jre subdir → JDK root)
    val rawJavaHome: String = System.getProperty("java.home") ?: System.getenv("JAVA_HOME") ?: ""
    val javaHome: String = File(rawJavaHome).let { f ->
        if (f.name == "jre") f.parentFile.absolutePath else f.absolutePath
    }

    doLast {
        /** Runs a process and throws if it exits non-zero. */
        fun runCmd(vararg args: String) {
            val exitCode = ProcessBuilder(*args)
                .inheritIO()
                .start()
                .waitFor()
            if (exitCode != 0) error("Command failed (exit $exitCode): ${args.joinToString(" ")}")
        }

        cmakeBuildDir.mkdirs()
        resourceOutputDir.mkdirs()

        logger.lifecycle("NativeDll: Configuring (JAVA_HOME=$javaHome)")
        runCmd(
            "cmake",
            "-S", nativeSrcDir.absolutePath,
            "-B", cmakeBuildDir.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DJAVA_HOME=$javaHome"
        )

        logger.lifecycle("NativeDll: Building...")
        runCmd("cmake", "--build", cmakeBuildDir.absolutePath, "--config", "Release")

        val dll = cmakeBuildDir.walk().firstOrNull { it.name == "toolkit-win-drag.dll" }
            ?: error("toolkit-win-drag.dll not found under $cmakeBuildDir after build")
        dll.copyTo(File(resourceOutputDir, "toolkit-win-drag.dll"), overwrite = true)
        logger.lifecycle("NativeDll: Installed → ${File(resourceOutputDir, "toolkit-win-drag.dll")}")
    }
}
