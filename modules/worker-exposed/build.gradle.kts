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
            implementation(libs.exposed.jdbc)
            implementation(libs.exposed.json)
            implementation(libs.exposed.kotlin.datetime)
            api(kotlinLibs.serialization.core)
        }

        jvmTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.coroutines.test)
            implementation(kotlinLibs.serialization.json)
            implementation(libs.h2)
            implementation(libs.sqlite)
        }
    }
}
