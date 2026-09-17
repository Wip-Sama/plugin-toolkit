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

// --- Dual Build Mode: Standalone Desktop Runner Tasks ---
tasks.register("run") {
    group = "application"
    description = "Runs this plugin inside the standalone Compose Desktop runner"
    dependsOn(":apps:standaloneRunner:run")
}

tasks.register("createDistributable") {
    group = "compose desktop"
    description = "Generates the unpacked, runnable native standalone application directory"
    dependsOn(":apps:standaloneRunner:createDistributable")
}

tasks.register("packageStandalone") {
    group = "compose desktop"
    description = "Packages this plugin as a standalone desktop distributable"
    dependsOn(":apps:standaloneRunner:packageDistributionForCurrentOS")
}

tasks.register("packageStandalonePortable") {
    group = "compose desktop"
    description = "Packages this plugin as a portable ZIP standalone distribution"
    dependsOn(":apps:standaloneRunner:packagePortableZip")
}

