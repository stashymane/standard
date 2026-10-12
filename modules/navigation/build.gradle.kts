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
            api(ktorLibs.resources)

            implementation(composeLibs.jb.runtime)
            implementation(composeLibs.jb.ui)

            implementation(kotlinLibs.serialization.core)
            implementation(composeLibs.androidx.navigation3.runtime)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }

        wasmJsMain.dependencies {
            implementation(kotlinLibs.browser)
        }
    }
}
