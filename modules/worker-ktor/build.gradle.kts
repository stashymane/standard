plugins {
    alias(kotlinLibs.plugins.multiplatform)

    id("multiplatform.target.jvm")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
}

kotlin {
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(projects.modules.workerCore)
            api(kotlinLibs.serialization.core)
            implementation(ktorLibs.server.core)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.coroutines.test)
            implementation(kotlinLibs.serialization.json)
            implementation(ktorLibs.server.testHost)
        }
    }
}
