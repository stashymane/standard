import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(androidLibs.plugins.library)

    id("multiplatform.target.jvm")
    id("multiplatform.target.wasmJs.browser")
    id("multiplatform.target.js.browser")
    id("multiplatform.target.androidLibrary")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
    alias(libs.plugins.dokka)
    id("plugins.dokka")
}

kotlin {
    explicitApi()

    android {
        namespace = "dev.stashy.standard.util.time"
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
    }

    js {
        binaries.executable()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }
    }
}
