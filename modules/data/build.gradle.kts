import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)
    alias(kotlinLibs.plugins.atomicfu)

    id("multiplatform.target.jvm")
    id("multiplatform.target.wasmJs")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlinLibs.coroutines.core)
            implementation(kotlinLibs.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.coroutines.test)
        }
    }
}
