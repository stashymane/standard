package dev.stashy.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

public class GroupStack<Key : GroupNavKey<Group>, Group : Any>(
    public val group: Group,
    public val entries: SnapshotStateList<Key> = mutableStateListOf(),
)
