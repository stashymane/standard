import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)
    alias(kotlinLibs.plugins.composeCompiler)
    alias(composeLibs.plugins.compose)
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

configure<ComposeExtension> {
    configure<ResourcesExtension> {
        packageOfResClass = "dev.stashy.standard.ui.components"
    }
}

kotlin {
    explicitApi()

    android {
        namespace = "dev.stashy.standard.ui.components"
        androidResources.enable = true
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
            implementation(projects.modules.uiGradients)
            implementation(projects.modules.utilTime)

            implementation(composeLibs.jb.runtime)
            implementation(composeLibs.jb.components.resources)
            implementation(composeLibs.jb.foundation)
            implementation(composeLibs.jb.material3)
            implementation(composeLibs.jb.uiToolingPreview)

            implementation(kotlinLibs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }
    }
}

dependencies {
    "androidRuntimeClasspath"(composeLibs.jb.uiTooling)
}
