import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)

    id("multiplatform.target.jvm")
    id("multiplatform.target.wasmJs")
    id("multiplatform.target.js")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
    alias(libs.plugins.dokka)
    id("plugins.dokka")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlinLibs.coroutines.core)
        }
        
        commonTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.coroutines.test)
        }
    }
}
