package dev.stashy.navigation.resources

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

val defaultResourcesFormat: ResourcesFormat = ResourcesFormat()

@OptIn(ExperimentalSerializationApi::class)
private object SerialNameLookup : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = error("unused")
}

/**
 * Builds a resource location (Ktor [fullPath]: encoded path and query)
 * for a [@Resource][Resource]-annotated serializable instance.
 */
@OptIn(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun <T : Any> T.toResourcePath(resourcesFormat: ResourcesFormat = defaultResourcesFormat): String {
    val serializer = this::class.serializer() as KSerializer<T>
    val builder = URLBuilder()
    href(resourcesFormat, serializer, this, builder)
    val full = builder.build().fullPath
    return normalizeResourceLocation(full.ifEmpty { "/" })
}

/**
 * Resolves a resource location to a [@Resource][Resource]-annotated subtype of [T].
 */
inline fun <reified T : Any> fromResourcePath(
    location: String,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
): T? = fromResourcePath(location, serializer(), resourcesFormat)

@Suppress("UNCHECKED_CAST")
fun <T : Any> fromResourcePath(
    location: String,
    rootSerializer: KSerializer<T>,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
): T? {
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
            resourcesFormat.decodeFromParameters(child as KSerializer<T>, merged)
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
