plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.serialization)
    alias(kotlinLibs.plugins.atomicfu)

    id("multiplatform.target.jvm")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
}

kotlin {
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            implementation(kotlinLibs.coroutines.core)
            api(kotlinLibs.serialization.core)
            implementation(kotlinLibs.datetime)
            implementation(projects.modules.cron)
        }

        commonTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.coroutines.test)
            implementation(kotlinLibs.serialization.json)
        }
    }
}
