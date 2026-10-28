package dev.stashy.worker.exposed

import kotlinx.serialization.json.Json

internal val TestJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
