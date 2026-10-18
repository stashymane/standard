package dev.stashy.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/**
 * Peer-set identity for group transitions (siblings share the same value).
 */
public object GroupPeerSetKey : NavMetadataKey<Any>

/**
 * Declaration index among siblings in the same peer set (for directional group transitions).
 */
public object GroupPeerIndexKey : NavMetadataKey<Int>

/**
 * Metadata for [group]’s peer set and index (no [NavGroup] exposure).
 */
internal fun groupMetadata(group: NavGroup): Map<String, Any> = metadata {
    put(GroupPeerSetKey, group.peerSetId)
    put(GroupPeerIndexKey, group.peerIndex)
}
