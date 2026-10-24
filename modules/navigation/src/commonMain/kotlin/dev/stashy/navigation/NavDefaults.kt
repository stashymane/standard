package dev.stashy.navigation

import kotlinx.serialization.json.Json

internal object NavDefaults {
    internal val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}
