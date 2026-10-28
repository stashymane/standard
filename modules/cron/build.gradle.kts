import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)

    id("multiplatform.target.jvm")
    id("multiplatform.target.wasmJs")
    id("multiplatform.target.js")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            api(kotlinLibs.datetime)
            api(kotlinLibs.serialization.core)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.serialization.json)
        }
    }
}
