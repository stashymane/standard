plugins {
    alias(kotlinLibs.plugins.multiplatform)

    id("multiplatform.target.jvm")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
}

kotlin {
    explicitApi()

    sourceSets {
        jvmMain.dependencies {
            api(projects.modules.workerCore)
            api(libs.exposed.core)
            api(libs.exposed.kotlin.datetime)
            api(kotlinLibs.serialization.core)
        }
    }
}
