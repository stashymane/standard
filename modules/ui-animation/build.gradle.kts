import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)
    alias(kotlinLibs.plugins.composeCompiler)
    alias(composeLibs.plugins.compose)
    alias(androidLibs.plugins.library)

    id("multiplatform.target.jvmDesktop")
    id("multiplatform.target.wasmJs.browser")
    id("multiplatform.target.js.browser")
    id("multiplatform.target.androidLibrary")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
}

kotlin {
    explicitApi()

    android {
        namespace = "dev.stashy.standard.ui.animation"
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(composeLibs.jb.runtime)
            implementation(composeLibs.jb.foundation)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }
    }
}
