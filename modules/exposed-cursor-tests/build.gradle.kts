plugins {
    alias(kotlinLibs.plugins.multiplatform)

    id("multiplatform.target.jvm")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.modules.exposedCursor)
            api(kotlinLibs.test)
            api(kotlinLibs.test.junit)
            api(kotlinLibs.coroutines.core)
            api(kotlinLibs.coroutines.test)
            api(libs.exposed.kotlin.datetime)
        }
    }
}
