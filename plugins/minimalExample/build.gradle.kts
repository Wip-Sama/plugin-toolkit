plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

sourceSets.main {
    kotlin.srcDir("build/generated/ksp/main/kotlin")
    resources.srcDir("build/generated/ksp/main/resources")
}

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":plugin-api"))
    ksp(project(":plugin-api"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// ── Standalone Mode Tasks ──────────────────────────────────────────────────
// Allows running and packaging this plugin in standalone desktop mode via :apps:standaloneRunner.
// In standard mode, this module remains a pure Kotlin JVM library without Compose/GUI dependencies.
tasks.register("run") {
    group = "application"
    description = "Runs this plugin as a standalone desktop app via :apps:standaloneRunner"
    dependsOn(":apps:standaloneRunner:run")
}

tasks.register("createDistributable") {
    group = "distribution"
    description = "Generates the unpacked, runnable native standalone application directory"
    dependsOn(":apps:standaloneRunner:createDistributable")
}

tasks.register("packageStandalone") {
    group = "distribution"
    description = "Packages this plugin as a standalone native desktop installer/app"
    dependsOn(":apps:standaloneRunner:packageDistributionForCurrentOS")
}

tasks.register("packageStandalonePortable") {
    group = "distribution"
    description = "Packages this plugin as a standalone portable ZIP distribution"
    dependsOn(":apps:standaloneRunner:packagePortableZip")
}
