package dev.stashy.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/**
 * [NavEntry][androidx.navigation3.runtime.NavEntry] metadata key for the destination’s [GroupId].
 */
public object GroupIdKey : NavMetadataKey<GroupId>

/**
 * Metadata map containing [GroupIdKey] → [group].
 */
public fun groupMetadata(group: GroupId): Map<String, Any> = metadata {
    put(GroupIdKey, group)
}

/**
 * Metadata map containing [GroupIdKey] when this destination is a [NavDestination.Group].
 */
public fun NavDestination.groupMetadata(): Map<String, Any> {
    val group = (this as? NavDestination.Group)?.group ?: return emptyMap()
    return groupMetadata(group)
}
