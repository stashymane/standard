plugins {
    alias(kotlinLibs.plugins.multiplatform)

    id("multiplatform.target.jvm")

    alias(libs.plugins.mavenPublish)
    id("plugins.publish.github")
    alias(libs.plugins.dokka)
    id("plugins.dokka")
}

kotlin {
    explicitApi()

    sourceSets {
        jvmMain.dependencies {
            api(projects.modules.exposedCursor)
            api(libs.exposed.r2dbc)
        }

        jvmTest.dependencies {
            implementation(projects.modules.exposedCursorTests)
            implementation(libs.r2dbc.h2)
        }
    }
}
