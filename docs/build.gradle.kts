plugins {
    alias(libs.plugins.dokka)
    id("plugins.dokka")
}

dependencies {
    dokka(projects.modules.cron)
    dokka(projects.modules.data)
    dokka(projects.modules.exposedCursor)
    dokka(projects.modules.exposedCursorJdbc)
    dokka(projects.modules.exposedCursorR2dbc)
    dokka(projects.modules.exposedModel)
    dokka(projects.modules.exposedUtil)
    dokka(projects.modules.model)
    dokka(projects.modules.navigation)
    dokka(projects.modules.utilTime)
    dokka(projects.modules.uiAnimation)
    dokka(projects.modules.uiComponents)
    dokka(projects.modules.uiGradients)
    dokka(projects.modules.uiResponsive)
    dokka(projects.modules.workerCore)
    dokka(projects.modules.workerExposedCore)
    dokka(projects.modules.workerExposedJdbc)
    dokka(projects.modules.workerExposedR2dbc)
    dokka(projects.modules.workerKtor)
}

dokka {
    moduleName.set(rootProject.name)
}
