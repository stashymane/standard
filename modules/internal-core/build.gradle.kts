import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)

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

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }
    }
}
