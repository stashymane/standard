package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey

/**
 * A destination rendered above the current screen that is never addressable.
 *
 * Overlay routes are ephemeral: they can only be entered through in-app navigation, are never
 * encoded into a URL, and are dismissed automatically when navigating elsewhere.
 */
public interface OverlayNavKey : NavKey
