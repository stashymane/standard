plugins {
    alias(kotlinLibs.plugins.multiplatform)

    id("multiplatform.target.jvm")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.modules.workerCore)
            api(kotlinLibs.test)
            api(kotlinLibs.test.junit)
            api(kotlinLibs.coroutines.core)
            api(kotlinLibs.coroutines.test)
            api(kotlinLibs.serialization.json)
        }
    }
}
