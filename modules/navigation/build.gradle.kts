import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)
    alias(kotlinLibs.plugins.composeCompiler)
    alias(composeLibs.plugins.compose)
    alias(androidLibs.plugins.library)

    id("multiplatform.target.jvmDesktop")
    id("multiplatform.target.wasmJs")
    id("multiplatform.target.androidLibrary")
}

kotlin {
    android {
        namespace = "dev.stashy.navigation"
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(composeLibs.jb.runtime)
            api(ktorLibs.resources)
            api(kotlinLibs.serialization.core)

            implementation(composeLibs.jb.ui)
        }

        wasmJsMain.dependencies {
            implementation(kotlinLibs.browser)
        }
    }
}
