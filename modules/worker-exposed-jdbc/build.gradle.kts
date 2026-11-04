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
            api(projects.modules.workerExposedCore)
            api(libs.exposed.jdbc)
            api(kotlinLibs.coroutines.core)
        }

        jvmTest.dependencies {
            implementation(projects.modules.workerExposedTests)
            implementation(libs.h2)
            implementation(libs.sqlite)
        }
    }
}
