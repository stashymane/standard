package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey

public interface GroupNavKey<G : Any> : NavKey {
    public val group: G?
}
