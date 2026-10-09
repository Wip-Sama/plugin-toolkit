plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover)
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
            verify {
                rule {
                    minBound(50) // will increase in the future
                }
            }
        }
    }
}

// Aggregate coverage from all subprojects into the root report
dependencies {
    kover(project(":shared:core"))
    kover(project(":shared:logic"))
    kover(project(":shared:gui"))
    kover(project(":plugin-api"))
    kover(project(":apps:cliApp"))
    kover(project(":plugins:completeExample"))
    kover(project(":plugins:minimalExample"))
}
