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
            api(projects.modules.model)
            implementation(libs.exposed.core)
        }

        jvmTest.dependencies {
            implementation(kotlinLibs.test)
            implementation(kotlinLibs.test.junit)
            implementation(libs.exposed.jdbc)
            implementation(libs.h2)
            implementation(libs.sqlite)
        }
    }
}
