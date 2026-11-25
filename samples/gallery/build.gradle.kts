import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)
    alias(kotlinLibs.plugins.composeCompiler)
    alias(composeLibs.plugins.compose)
    alias(androidLibs.plugins.library)
    alias(libs.plugins.koinCompiler)

    id("multiplatform.target.jvm")
    id("multiplatform.target.wasmJs.browser")
    id("multiplatform.target.js.browser")
    id("multiplatform.target.androidLibrary")
}

kotlin {
    android {
        namespace = "dev.stashy.standard.samples.gallery"
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
            implementation(projects.modules.uiComponents)

            implementation(composeLibs.jb.runtime)
            implementation(composeLibs.jb.foundation)
            implementation(composeLibs.jb.ui)
            implementation(composeLibs.jb.material3)
            implementation(composeLibs.jb.components.resources)
            implementation(composeLibs.jb.uiToolingPreview)

            implementation(composeLibs.androidx.navigation3.ui)
            implementation(composeLibs.lifecycle.runtime.compose)
            implementation(composeLibs.lifecycle.viewmodel.navigation3)

            implementation(kotlinLibs.serialization.core)
            implementation(kotlinLibs.serialization.json)
            implementation(ktorLibs.resources)

            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        webMain.dependencies {
            implementation(kotlinLibs.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(composeLibs.jb.uiTooling)
}

compose.desktop {
    application {
        mainClass = "dev.stashy.standard.samples.gallery.MainKt"
    }
}
