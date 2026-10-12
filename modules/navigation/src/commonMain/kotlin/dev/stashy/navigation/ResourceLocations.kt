package dev.stashy.navigation

import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.fullPath
import io.ktor.http.parseQueryString

/**
 * Splits a resource location (Ktor [io.ktor.http.Url.fullPath]) into path and raw query (without `?`).
 * Fragment (`#…`) is stripped.
 */
fun splitResourceLocation(location: String): Pair<String, String?> {
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
fun normalizeResourcePath(path: String): String {
    val trimmed = path.trimEnd('/')
    return when {
        trimmed.isEmpty() -> "/"
        trimmed.startsWith('/') -> trimmed
        else -> "/$trimmed"
    }
}

/** @see normalizeResourcePath */
fun normalizePath(path: String): String = normalizeResourcePath(path)

/**
 * Normalizes path and query so equivalent locations compare equal (stable query encoding).
 */
fun normalizeResourceLocation(location: String): String {
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
