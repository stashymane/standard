plugins {
    alias(kotlinLibs.plugins.multiplatform)
    alias(kotlinLibs.plugins.benchmark)

    id("multiplatform.target.jvm")
    id("multiplatform.target.js")
    id("multiplatform.target.wasmJs")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.modules.internalCore)
            implementation(kotlinLibs.benchmark.runtime)
        }
    }
}

benchmark {
    configurations {
        named("main") {
            warmups = 1
            iterations = 1
            iterationTime = 5
            iterationTimeUnit = "s"
        }
    }

    targets {
        register("jvm")
        register("js")
        register("wasmJs")
    }
}
