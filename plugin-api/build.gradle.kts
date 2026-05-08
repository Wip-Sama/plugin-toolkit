plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            
            // Processor dependencies
            implementation(libs.ksp.api)
            implementation(libs.kotlinpoet)
            implementation(libs.kotlinpoet.ksp)
        }
    }
}
