package dev.stashy.navigation

import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.fullPath
import io.ktor.resources.Resource
import io.ktor.resources.href
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.findPolymorphicSerializer
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

private val resourcesFormat = ResourcesFormat()

@OptIn(ExperimentalSerializationApi::class)
private object SerialNameLookup : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = error("unused")
}

fun normalizePath(path: String): String {
    val trimmed = path.trimEnd('/')
    return when {
        trimmed.isEmpty() -> "/"
        trimmed.startsWith('/') -> trimmed
        else -> "/$trimmed"
    }
}

/**
 * Builds a URL path for a Ktor [@Resource][Resource]-annotated serializable instance.
 */
@OptIn(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun <T : Any> T.toResourcePath(): String {
    val serializer = this::class.serializer() as KSerializer<T>
    val builder = URLBuilder()
    href(resourcesFormat, serializer, this, builder)
    return normalizePath(builder.build().fullPath.ifEmpty { "/" })
}

/**
 * Resolves a URL path to a [@Resource][Resource]-annotated subtype of [T].
 */
inline fun <reified T : Any> fromResourcePath(path: String): T? =
    fromResourcePath(path, serializer())

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun <T : Any> fromResourcePath(path: String, rootSerializer: KSerializer<T>): T? {
    val normalized = normalizePath(path)
    return resourceSerializers(rootSerializer).firstNotNullOfOrNull { child ->
        val pattern = resourcesFormat.encodeToPathPattern(child)
        val parameters = matchResourcePath(pattern, normalized) ?: return@firstNotNullOfOrNull null
        runCatching {
            resourcesFormat.decodeFromParameters(child as KSerializer<T>, parameters)
        }.getOrNull()
    }
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
private fun resourceSerializers(serializer: KSerializer<*>): List<KSerializer<*>> {
    val descriptor = serializer.descriptor
    return when (descriptor.kind) {
        is PolymorphicKind.SEALED -> {
            val polymorphic = serializer as AbstractPolymorphicSerializer<Any>
            val subtypes = descriptor.getElementDescriptor(1)
            buildList {
                for (index in 0 until subtypes.elementsCount) {
                    val child = polymorphic.findPolymorphicSerializer(
                        SerialNameLookup,
                        subtypes.getElementName(index)
                    ) as KSerializer<*>
                    addAll(resourceSerializers(child))
                }
            }
        }

        else -> if (descriptor.annotations.any { it is Resource }) listOf(serializer) else emptyList()
    }
}

/**
 * Matches a Ktor [ResourcesFormat.encodeToPathPattern] result (no leading slash) to a normalized path.
 */
private fun matchResourcePath(pattern: String, normalizedPath: String): Parameters? {
    if (!pattern.contains('{')) {
        val expected = normalizePath(if (pattern.isEmpty()) "/" else "/$pattern")
        return if (expected == normalizedPath) Parameters.Empty else null
    }

    val patternParts = pattern.split('/')
    val pathParts = if (normalizedPath == "/") emptyList() else normalizedPath.removePrefix("/").split('/')
    if (patternParts.size != pathParts.size) return null

    val parameters = ParametersBuilder()
    for ((part, value) in patternParts.zip(pathParts)) {
        if (part.startsWith('{') && part.endsWith('}')) {
            val name = part.substring(1, part.lastIndex)
                .removeSuffix("?")
                .removeSuffix("...")
            parameters.append(name, value)
        } else if (part != value) {
            return null
        }
    }
    return parameters.build()
}
