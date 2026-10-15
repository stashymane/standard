import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
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

configure<ComposeExtension> {
    configure<ResourcesExtension> {
        packageOfResClass = "dev.stashy.standard.util.time"
    }
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
        commonMain.dependencies {
            implementation(composeLibs.jb.runtime)
            implementation(composeLibs.jb.components.resources)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }
    }
}
