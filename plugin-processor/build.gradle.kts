plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "org.wip.plugintoolkit"
version = libs.versions.app.get()

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

sourceSets {
    main {
        kotlin.srcDir("../plugin-api/src/jvmMain/kotlin")
        kotlin.include("org/wip/plugintoolkit/api/processor/**")
        resources.srcDir("../plugin-api/src/jvmMain/resources")
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.kotlinx.serialization.json)
}
