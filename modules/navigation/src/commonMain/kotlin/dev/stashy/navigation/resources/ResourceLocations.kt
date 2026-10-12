package dev.stashy.navigation.resources

import io.ktor.http.*

/**
 * Splits a resource location (Ktor [fullPath]) into path and raw query (without `?`).
 * Fragment (`#…`) is stripped.
 */
internal fun splitResourceLocation(location: String): Pair<String, String?> {
    val withoutHash = location.substringBefore('#')
    val queryStart = withoutHash.indexOf('?')
    return if (queryStart < 0) {
        withoutHash to null
    } else {
        withoutHash.substring(0, queryStart) to
                withoutHash.substring(queryStart + 1).takeIf { it.isNotEmpty() }
    }
}

/** Normalizes only the path portion (leading slash, no trailing slash except root). */
internal fun normalizeResourcePath(path: String): String {
    val trimmed = path.trimEnd('/')
    return when {
        trimmed.isEmpty() -> "/"
        trimmed.startsWith('/') -> trimmed
        else -> "/$trimmed"
    }
}

/** @see normalizeResourcePath */
internal fun normalizePath(path: String): String = normalizeResourcePath(path)

/**
 * Normalizes path and query so equivalent locations compare equal (stable query encoding).
 */
internal fun normalizeResourceLocation(location: String): String {
    val (path, query) = splitResourceLocation(location)
    val normalizedPath = normalizeResourcePath(path)
    if (query.isNullOrEmpty()) return normalizedPath

    val queryParams = parseQueryString(query)
    if (queryParams.isEmpty()) return normalizedPath

    val builder = URLBuilder().apply {
        if (normalizedPath == "/") {
            pathSegments = listOf("")
        } else {
            appendPathSegments(*normalizedPath.removePrefix("/").split('/').toTypedArray())
        }
        parameters.appendAll(queryParams)
    }
    val full = builder.build().fullPath
    return full.ifEmpty { normalizedPath }
}

internal fun mergeResourceParameters(pathParams: Parameters, queryParams: Parameters): Parameters =
    ParametersBuilder().apply {
        appendAll(queryParams)
        pathParams.forEach { name, values ->
            remove(name)
            appendAll(name, values)
        }
    }.build()
