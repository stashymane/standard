package dev.stashy.worker

import kotlinx.serialization.json.Json

internal val TestJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
