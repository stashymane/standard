package dev.stashy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import kotlin.reflect.KClass

/**
 * Compiled navigation graph: entry provider plus group topology from [navGraph].
 *
 * Destinations are grouped by key **type** (`KClass`); all instances of a type share one group.
 */
public class NavGraph<T : Any> internal constructor(
    public val entryProvider: (T) -> NavEntry<T>,
    private val typeGroups: Map<KClass<*>, NavGroup>,
    internal val rootGroups: List<NavGroup>,
) {
    /**
     * @return group that owns [key]’s type.
     * @throws IllegalArgumentException if that type was not registered
     */
    internal fun groupOf(key: Any): NavGroup = typeGroups[key::class]
        ?: throw IllegalArgumentException("No group registered for key type: ${key::class.simpleName}")
}

/**
 * Builds a [NavGraph] with hierarchical [group] scopes and typed [NavEntryProviderScope.entry]
 * content (Navigation 3 entry-provider style).
 */
public fun <T : Any> navGraph(
    fallback: (unknownScreen: T) -> NavEntry<T> =
        { throw IllegalStateException("Unknown screen $it") },
    builder: NavEntryProviderScope<T>.() -> Unit,
): NavGraph<T> = NavEntryProviderScope<T>().apply(builder).build(fallback)

@DslMarker
public annotation class NavEntryDsl

@NavEntryDsl
public class NavEntryProviderScope<T : Any> internal constructor() {
    private val rootPeerSetId: Any = Any()
    private val rootGroups = mutableListOf<NavGroup>()
    private val typeGroups = mutableMapOf<KClass<*>, NavGroup>()
    private val registrations = mutableListOf<EntryProviderScope<T>.() -> Unit>()

    /**
     * Declares a peer group at this scope. Sibling [group] blocks retain stacks when switched.
     */
    public fun group(block: NavGroupScope<T>.() -> Unit) {
        val group = NavGroup(
            parent = null,
            peerIndex = rootGroups.size,
            peerSetId = rootPeerSetId,
        )
        rootGroups.add(group)
        NavGroupScope(group, typeGroups, registrations).block()
    }

    internal fun build(fallback: (T) -> NavEntry<T>): NavGraph<T> {
        require(rootGroups.isNotEmpty()) { "navGraph requires at least one group { }" }

        val provider = entryProvider(fallback) {
            for (register in registrations) {
                register()
            }
        }

        return NavGraph(
            entryProvider = provider,
            typeGroups = typeGroups.toMap(),
            rootGroups = rootGroups.toList(),
        )
    }
}

@NavEntryDsl
public class NavGroupScope<T : Any> internal constructor(
    private val group: NavGroup,
    private val typeGroups: MutableMap<KClass<*>, NavGroup>,
    private val registrations: MutableList<EntryProviderScope<T>.() -> Unit>,
) {
    /**
     * Nested peer set under [group]. Sibling nested groups retain stacks when switched;
     * backing out of a nested group clears it.
     */
    public fun group(block: NavGroupScope<T>.() -> Unit) {
        val child = NavGroup(
            parent = group,
            peerIndex = group.childGroups.size,
            peerSetId = group.nestedPeerSetId,
        )
        group.childGroups.add(child)
        NavGroupScope(child, typeGroups, registrations).block()
    }

    public inline fun <reified K : T> entry(
        noinline contentKey: (key: K) -> Any = { it.toString() },
        metadata: Map<String, Any> = emptyMap(),
        noinline content: @Composable (K) -> Unit,
    ) {
        entry(contentKey, { metadata }, content)
    }

    public inline fun <reified K : T> entry(
        noinline contentKey: (key: K) -> Any = { it.toString() },
        noinline metadata: (K) -> Map<String, Any>,
        noinline content: @Composable (K) -> Unit,
    ) {
        addTypeEntry(K::class, contentKey, metadata, content)
    }

    @PublishedApi
    internal fun <K : T> addTypeEntry(
        clazz: KClass<K>,
        contentKey: (K) -> Any,
        metadata: (K) -> Map<String, Any>,
        content: @Composable (K) -> Unit,
    ) {
        require(clazz !in typeGroups) {
            "An entry with the same type has already been added: ${clazz.simpleName}"
        }
        typeGroups[clazz] = group

        registrations.add {
            addEntryProvider(
                clazz = clazz,
                clazzContentKey = contentKey,
                metadata = metadata,
                content = content,
            )
        }
    }
}
