package dev.stashy.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.resources.normalizeResourceLocation
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(ExperimentalWasmJsInterop::class)
internal class BrowserHistorySession<Entry : NavKey>(
    private val backStack: List<Entry>,
    private val pathOf: (Entry) -> String,
    private val parsePath: (String) -> Entry,
    private val applyEntry: (Entry) -> Unit,
) {
    /** How many `pushState` entries this session owns above the initial replaceState. */
    private var navigableDepth = 0
    private var lastSize = 1
    private var suppressPopState = false
    private var popStateListener: ((Event) -> Unit)? = null

    private fun currentLocation(): String =
        normalizeResourceLocation(window.location.pathname + window.location.search)

    suspend fun bind() {
        val initial = parsePath(currentLocation())
        applyEntry(initial)
        window.history.replaceState(null, "", pathOf(initial))
        navigableDepth = 0
        lastSize = backStack.size

        snapshotFlow { backStack.toList() }
            .collect { stack -> syncStackToHistory(stack) }
    }

    private fun syncStackToHistory(stack: List<Entry>) {
        val path = pathOf(stack.last())
        val locationPath = currentLocation()

        if (normalizeResourceLocation(path) == locationPath) {
            lastSize = stack.size
            return
        }

        when {
            stack.size < lastSize -> popHistory(lastSize - stack.size, path)
            stack.size > lastSize -> {
                window.history.pushState(null, "", path)
                navigableDepth++
            }

            else -> window.history.replaceState(null, "", path)
        }
        lastSize = stack.size
    }

    private fun popHistory(steps: Int, path: String) {
        val goBack = steps.coerceAtMost(navigableDepth)
        if (goBack > 0) {
            suppressPopState = true
            navigableDepth -= goBack
            window.history.go(-goBack)
        }
        // No session history left to pop (e.g. deep link): align URL in place.
        if (goBack < steps) {
            window.history.replaceState(null, "", path)
        }
    }

    fun attachPopStateListener() {
        val listener: (Event) -> Unit = {
            if (suppressPopState) {
                suppressPopState = false
                lastSize = backStack.size
            } else {
                val location = currentLocation()
                val sizeBefore = backStack.size
                applyEntry(parsePath(location))
                val sizeAfter = backStack.size
                navigableDepth = (navigableDepth + (sizeAfter - sizeBefore)).coerceAtLeast(0)
                lastSize = sizeAfter
            }
        }
        popStateListener = listener
        window.addEventListener("popstate", listener)
    }

    fun detachPopStateListener() {
        popStateListener?.let { window.removeEventListener("popstate", it) }
        popStateListener = null
    }
}
