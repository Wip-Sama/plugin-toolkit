plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
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
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Wip-Sama/plugin-toolkit")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
