enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("conventions")
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        register("kotlinLibs") {
            from(files("gradle/kotlin.versions.toml"))
        }
        register("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.6.0")
        }
        register("composeLibs") {
            from(files("gradle/compose.versions.toml"))
        }
        register("androidLibs") {
            from(files("gradle/android.versions.toml"))
        }
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    "benchmarks",
    "docs",
    "modules:coroutine-util",
    "modules:cron",
    "modules:data",
    "modules:exposed-cursor",
    "modules:exposed-cursor-jdbc",
    "modules:exposed-cursor-r2dbc",
    "modules:exposed-cursor-tests",
    "modules:exposed-model",
    "modules:exposed-util",
    "modules:internal-core",
    "modules:model",
    "modules:navigation",
    "modules:util-time",
    "modules:ui-animation",
    "modules:ui-components",
    "modules:ui-gradients",
    "modules:ui-responsive",
    "modules:worker-core",
    "modules:worker-exposed-core",
    "modules:worker-exposed-jdbc",
    "modules:worker-exposed-r2dbc",
    "modules:worker-exposed-tests",
    "modules:worker-ktor",
    "samples:gallery",
    "samples:navigation-sample",
)

rootProject.name = "standard"
