# General stability for Multiplatform/Compose
-keepattributes Signature, Exceptions, *Annotation*, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable

# Preserve ServiceLoader directory structure
-keepdirectories META-INF/services

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$serializer {
    public static final **$serializer INSTANCE;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    *** serializer(...);
}
# Keep all @Serializable classes and their members
-keep @kotlinx.serialization.Serializable class * { *; }

# Ktor engines and ServiceLoader
-keepnames class io.ktor.**
-keepnames interface io.ktor.**
-keep class io.ktor.serialization.kotlinx.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
-dontwarn io.ktor.**

# JNA
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# DBus-Java
-keep class org.freedesktop.dbus.** { *; }
-keep interface org.freedesktop.dbus.** { *; }
-keep class cx.ath.matthew.** { *; }
-dontwarn org.freedesktop.dbus.**
-dontwarn cx.ath.matthew.**

# Koin - DI framework
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }
-dontwarn org.koin.**
# Keep classes that are instantiated via Koin (best effort)
-keep class org.wip.plugintoolkit.features.**.logic.** { *; }
-keep class org.wip.plugintoolkit.features.**.viewmodel.** { *; }

# Kermit - Logging
-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# SLF4J
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Compose Resources
-keep class plugintoolkit.composeapp.generated.resources.** { *; }

# Project Models and Persistence
-keep class org.wip.plugintoolkit.features.**.model.** { *; }
-keep class org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence { *; }

# Plugin API and Entry Points
-keep class org.wip.plugintoolkit.api.** { *; }
-keep interface org.wip.plugintoolkit.api.** { *; }
-keepnames class * implements org.wip.plugintoolkit.api.PluginEntry
-keep class * implements org.wip.plugintoolkit.api.PluginEntry { *; }
-keep class * implements org.wip.plugintoolkit.api.DataProcessor { *; }
-keep class * implements org.wip.plugintoolkit.api.PluginContext { *; }
-keepclassmembers class * implements org.wip.plugintoolkit.api.PluginEntry {
    public <init>();
}
# Keep all public methods in DataProcessor implementations as they may be called via reflection
-keepclassmembers class * implements org.wip.plugintoolkit.api.DataProcessor {
    public <methods>;
}

# Keep main class
-keepclassmembers class org.wip.plugintoolkit.MainKt {
    public static void main(java.lang.String[]);
}

# AndroidX and Navigation
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**
