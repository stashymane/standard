package dev.stashy.navigation

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Identifies a navigation group (for example a bottom-tab back stack).
 */
@JvmInline
@Serializable
public value class GroupId(public val value: String) {
    public companion object {
        public val Default: GroupId = GroupId("default")
    }
}
