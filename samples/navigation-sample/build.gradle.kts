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
}

kotlin {
    android {
        namespace = "dev.stashy.standard.samples.navigation"
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
    }

    js {
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.modules.navigation)
            implementation(projects.modules.uiAnimation)

            implementation(composeLibs.jb.runtime)
            implementation(composeLibs.jb.foundation)
            implementation(composeLibs.jb.ui)
            implementation(composeLibs.jb.material3)

            implementation(composeLibs.androidx.navigation3.ui)
            implementation(composeLibs.lifecycle.runtime.compose)
            implementation(composeLibs.lifecycle.viewmodel.navigation3)

            implementation(kotlinLibs.serialization.core)
            implementation(kotlinLibs.serialization.json)
            implementation(ktorLibs.resources)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        webMain.dependencies {
            implementation(kotlinLibs.browser)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.stashy.standard.samples.navigation.MainKt"
    }
}
