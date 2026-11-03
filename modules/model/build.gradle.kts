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

    sourceSets {
        commonMain.dependencies {
            implementation(kotlinLibs.serialization.core)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
        }
    }
}
