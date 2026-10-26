package dev.stashy.navigation.resources

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.findPolymorphicSerializer
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@PublishedApi
internal val defaultResourcesFormat: ResourcesFormat = ResourcesFormat()

@OptIn(ExperimentalSerializationApi::class)
private object SerialNameLookup : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = error("unused")
}

@OptIn(ExperimentalSerializationApi::class)
private object SerialNameLookupEncoder : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeValue(value: Any) = Unit
}

/**
 * Builds a resource location (Ktor [fullPath]: encoded path and query)
 * for a [@Resource][Resource]-annotated serializable instance.
 */
@PublishedApi
internal fun <T : Any> T.toResourcePath(
    rootSerializer: KSerializer<T>,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
): String {
    val serializer = resourcePathSerializer(rootSerializer, this)
    val builder = URLBuilder()
    href(resourcesFormat, serializer, this, builder)
    val full = builder.build().fullPath
    return normalizeResourceLocation(full.ifEmpty { "/" })
}

@OptIn(InternalSerializationApi::class)
private fun <T : Any> resourcePathSerializer(
    rootSerializer: KSerializer<T>,
    value: T,
): KSerializer<T> {
    val polymorphic = rootSerializer as? AbstractPolymorphicSerializer<T> ?: return rootSerializer
    val strategy = polymorphic.findPolymorphicSerializer(SerialNameLookupEncoder, value)
    check(strategy is KSerializer<T>) {
        "Resource route serializer must be a KSerializer, got ${strategy::class.simpleName}"
    }
    return strategy
}

/**
 * Resolves a resource location to a [@Resource][Resource]-annotated subtype of [T].
 */
@PublishedApi
internal inline fun <reified T : Any> fromResourcePath(
    location: String,
    rootSerializer: KSerializer<T>,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
): T? = decodeFromResourcePath(location, rootSerializer, resourcesFormat) as? T

@PublishedApi
internal fun decodeFromResourcePath(
    location: String,
    rootSerializer: KSerializer<*>,
    resourcesFormat: ResourcesFormat,
    accept: (Any) -> Boolean = { true },
): Any? {
    val (path, query) = splitResourceLocation(location)
    val normalizedPath = normalizeResourcePath(path)
    val queryParams = if (query.isNullOrEmpty()) Parameters.Empty else parseQueryString(query)

    return resourceSerializers(rootSerializer).firstNotNullOfOrNull { child ->
        val pattern = resourcesFormat.encodeToPathPattern(child)
        val pathParams = matchResourcePath(pattern, normalizedPath) ?: return@firstNotNullOfOrNull null

        val querySpecs = resourcesFormat.encodeToQueryParameters(child)
        for (spec in querySpecs) {
            if (!spec.isOptional && queryParams.getAll(spec.name).isNullOrEmpty()) {
                return@firstNotNullOfOrNull null
            }
        }

        val merged = mergeResourceParameters(pathParams, queryParams)
        runCatching {
            resourcesFormat.decodeFromParameters(child, merged)
        }.getOrNull()?.takeIf(accept)
    }
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
private fun resourceSerializers(serializer: KSerializer<*>): List<KSerializer<*>> {
    val descriptor = serializer.descriptor
    return when (descriptor.kind) {
        is PolymorphicKind.SEALED -> {
            val polymorphic = serializer as? AbstractPolymorphicSerializer<*> ?: return emptyList()
            val subtypes = descriptor.getElementDescriptor(1)
            buildList {
                for (index in 0 until subtypes.elementsCount) {
                    when (
                        val strategy = polymorphic.findPolymorphicSerializer(
                            SerialNameLookup,
                            subtypes.getElementName(index),
                        )
                    ) {
                        is KSerializer<*> -> addAll(resourceSerializers(strategy))
                    }
                }
            }
        }

        else -> if (descriptor.annotations.any { it is Resource }) listOf(serializer) else emptyList()
    }
}
