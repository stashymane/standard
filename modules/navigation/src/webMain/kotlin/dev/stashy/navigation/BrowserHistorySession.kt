package dev.stashy.navigation

import androidx.compose.runtime.snapshotFlow
import dev.stashy.navigation.resources.normalizeResourceLocation
import kotlinx.browser.window
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.w3c.dom.events.Event
import kotlin.time.Duration.Companion.milliseconds

internal class BrowserHistorySession(
    private val backStack: List<NavDestination>,
    private val entryStateStore: EntryStateStore,
    private val pathOf: (NavDestination) -> String,
    private val parsePath: (String) -> NavDestination,
    private val applyEntry: (NavDestination) -> Unit,
) {
    internal companion object {
        internal const val HISTORY_DEBOUNCE_MS: Long = 200L
    }

    /** How many `pushState` entries this session owns above the initial replaceState. */
    private var navigableDepth = 0
    private var lastSize = 1
    private var suppressPopState = false
    private var popStateListener: ((Event) -> Unit)? = null

    private fun currentLocation(): String =
        normalizeResourceLocation(window.location.pathname + window.location.search)

    private fun historyStatePayload(): String =
        encodeEntryStateSnapshot(entryStateStore.snapshot())

    private fun replaceState(path: String) =
        writeHistoryReplaceState(historyStatePayload(), path)

    private fun pushState(path: String) =
        writeHistoryPushState(historyStatePayload(), path)

    @OptIn(FlowPreview::class)
    suspend fun bind() {
        val initial = parsePath(currentLocation())
        applyEntry(initial)
        replaceState(pathOf(initial))
        navigableDepth = 0
        lastSize = backStack.size

        snapshotFlow { entryStateStore.snapshot() }
            .debounce(HISTORY_DEBOUNCE_MS.milliseconds)
            .collect {
                if (suppressPopState) return@collect
                val path = pathOf(backStack.last())
                if (normalizeResourceLocation(path) == currentLocation()) {
                    replaceState(path)
                }
            }
    }

    suspend fun syncFromBackStack() {
        snapshotFlow { backStack.toList() }
            .collect { stack -> syncStackToHistory(stack) }
    }

    private fun syncStackToHistory(stack: List<NavDestination>) {
        val path = pathOf(stack.last())
        val locationPath = currentLocation()

        if (normalizeResourceLocation(path) == locationPath) {
            lastSize = stack.size
            return
        }

        when {
            stack.size < lastSize -> popHistory(lastSize - stack.size, path)
            stack.size > lastSize -> {
                val previous = stack.getOrNull(stack.size - 2) ?: stack.first()
                replaceState(pathOf(previous))
                pushState(path)
                navigableDepth++
            }

            else -> replaceState(path)
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
        if (goBack < steps) {
            replaceState(path)
        }
    }

    fun attachPopStateListener() {
        val listener: (Event) -> Unit = { event ->
            if (suppressPopState) {
                suppressPopState = false
                lastSize = backStack.size
            } else {
                entryStateStore.restore(decodeEntryStateSnapshot(readPopStatePayload(event)))
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
