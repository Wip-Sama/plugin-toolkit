plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}


kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(project(":plugin-api"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
            implementation(libs.koin.test)
        }
        jvmMain.dependencies {
            implementation(libs.slf4j.simple)
            implementation(libs.kotlinx.datetime)
        }
    }
}

buildkonfig {
    packageName = "org.wip.plugintoolkit"
    objectName = "AppConfig"
    exposeObjectWithName = "AppConfig"

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

