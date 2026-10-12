package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey

interface GroupNavKey<G : Any> : NavKey {
    val group: G?
}
