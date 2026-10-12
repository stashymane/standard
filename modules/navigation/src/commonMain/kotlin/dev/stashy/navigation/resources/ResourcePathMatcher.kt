package dev.stashy.navigation.resources

import io.ktor.http.*

/**
 * Matches a Ktor [io.ktor.resources.serialization.ResourcesFormat.encodeToPathPattern] result
 * (no leading slash) to a [normalizeResourcePath] path.
 */
internal fun matchResourcePath(pattern: String, normalizedPath: String): Parameters? {
    val patternSegments = pattern.split('/').filter { it.isNotEmpty() }
    val pathSegments =
        if (normalizedPath == "/") emptyList()
        else normalizedPath.removePrefix("/").split('/').map { it.decodeURLPart() }

    val builder = ParametersBuilder()
    return if (matchSegments(patternSegments, 0, pathSegments, 0, builder)) builder.build() else null
}

private fun matchSegments(
    pattern: List<String>,
    patternIndex: Int,
    path: List<String>,
    pathIndex: Int,
    out: ParametersBuilder,
): Boolean {
    if (patternIndex == pattern.size) return pathIndex == path.size

    return when (val segment = parsePatternSegment(pattern[patternIndex])) {
        is Constant -> {
            if (pathIndex >= path.size || path[pathIndex] != segment.value) return false
            matchSegments(pattern, patternIndex + 1, path, pathIndex + 1, out)
        }

        is Parameter -> {
            if (segment.isTailcard) {
                if (pathIndex > path.size) return false
                val values = tailcardValues(path, pathIndex, segment.prefix.orEmpty()) ?: return false
                if (segment.name.isNotEmpty()) {
                    values.forEach { out.append(segment.name, it) }
                }
                patternIndex + 1 == pattern.size
            } else if (pathIndex >= path.size) {
                if (segment.isOptional) {
                    matchSegments(pattern, patternIndex + 1, path, pathIndex, out)
                } else {
                    false
                }
            } else {
                val captured = captureSegment(path[pathIndex], segment)
                when {
                    captured != null -> {
                        out.append(segment.name, captured)
                        matchSegments(pattern, patternIndex + 1, path, pathIndex + 1, out)
                    }

                    segment.isOptional ->
                        matchSegments(pattern, patternIndex + 1, path, pathIndex, out)

                    else -> false
                }
            }
        }
    }
}

private fun tailcardValues(path: List<String>, pathIndex: Int, prefix: String): List<String>? {
    if (pathIndex >= path.size) {
        return if (prefix.isEmpty()) emptyList() else null
    }
    val first = path[pathIndex]
    if (prefix.isNotEmpty() && !first.startsWith(prefix)) return null
    val firstValue = if (prefix.isEmpty()) first else first.drop(prefix.length)
    return buildList {
        add(firstValue)
        for (index in pathIndex + 1 until path.size) {
            add(path[index])
        }
    }
}

private fun captureSegment(pathSegment: String, segment: PatternSegment.Parameter): String? {
    var value = pathSegment
    segment.prefix?.let { prefix ->
        if (!value.startsWith(prefix)) return null
        value = value.drop(prefix.length)
    }
    segment.suffix?.let { suffix ->
        if (!value.endsWith(suffix)) return null
        value = value.dropLast(suffix.length)
    }
    return value
}

private sealed interface PatternSegment {
    data class Constant(val value: String) : PatternSegment

    data class Parameter(
        val name: String,
        val prefix: String?,
        val suffix: String?,
        val isOptional: Boolean,
        val isTailcard: Boolean,
    ) : PatternSegment
}

/** Same segment grammar as Ktor [PathSegmentSelectorBuilder.parseParameter]. */
private fun parsePatternSegment(value: String): PatternSegment {
    if (!value.contains('{') || !value.contains('}')) {
        return PatternSegment.Constant(value.decodeURLPart())
    }

    val prefixIndex = value.indexOf('{')
    val suffixIndex = value.lastIndexOf('}')
    val prefix = if (prefixIndex == 0) null else value.substring(0, prefixIndex)
    val suffix = if (suffixIndex == value.length - 1) null else value.substring(suffixIndex + 1)
    val signature = value.substring(prefixIndex + 1, suffixIndex)

    return when {
        signature.endsWith("...") -> {
            require(suffix.isNullOrEmpty()) { "Suffix after tailcard is not supported" }
            PatternSegment.Parameter(
                name = signature.dropLast(3),
                prefix = prefix,
                suffix = null,
                isOptional = false,
                isTailcard = true,
            )
        }

        signature.endsWith("?") -> PatternSegment.Parameter(
            name = signature.dropLast(1),
            prefix = prefix,
            suffix = suffix,
            isOptional = true,
            isTailcard = false,
        )

        else -> PatternSegment.Parameter(
            name = signature,
            prefix = prefix,
            suffix = suffix,
            isOptional = false,
            isTailcard = false,
        )
    }
}
