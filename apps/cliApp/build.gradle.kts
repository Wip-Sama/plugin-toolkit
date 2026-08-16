plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildkonfig)
    application
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:logic"))
    implementation(project(":plugin-api"))
    implementation(libs.clikt)
    implementation(libs.slf4j.simple)
    implementation(libs.kermit)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

buildkonfig {
    packageName = "org.wip.plugintoolkit.cli"
    objectName = "CliAppConfig"

    defaultConfigs {
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            "VERSION",
            libs.versions.app.get()
        )
    }
}

application {
    mainClass.set("org.wip.plugintoolkit.cli.MainKt")
}
