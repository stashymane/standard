package dev.stashy.data

import dev.stashy.data.source.map
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

inline fun <reified R> DataSource<String>.deserialize(format: StringFormat = Json) =
    map { format.decodeFromString<R>(it) }
