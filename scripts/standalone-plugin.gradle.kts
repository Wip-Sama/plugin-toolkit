import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

// Apply from a JVM plugin module after its dependencies have been declared.
tasks.register<Jar>("standaloneJar") {
    group = "distribution"
    description = "Builds an executable plugin JAR with its runtime dependencies."
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn("jar")
    from({ zipTree(tasks.named<Jar>("jar").get().archiveFile.get().asFile) })
    from({
        configurations.getByName("runtimeClasspath").map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest.attributes["Main-Class"] =
        "org.wip.plugintoolkit.api.standalone.StandalonePluginMainKt"
}
