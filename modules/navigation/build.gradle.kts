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

            implementation(kotlinLibs.serialization.core)
            implementation(composeLibs.androidx.navigation3.runtime)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.coroutines.test)
        }

        webMain.dependencies {
            implementation(kotlinLibs.browser)
        }
    }
}
