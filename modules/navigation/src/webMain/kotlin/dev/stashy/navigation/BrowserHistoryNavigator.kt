package dev.stashy.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.resources.normalizeResourceLocation
import dev.stashy.navigation.state.NavigationState
import kotlinx.browser.window
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.w3c.dom.events.Event
import kotlin.time.Duration.Companion.milliseconds

/**
 * Web [Navigator] implementation that drives the browser History API.
 *
 * Navigation actions update the browser URL and state, and browser back/forward events update
 * [dev.stashy.navigation.state.NavigationState].
 */
internal class BrowserHistoryNavigator(
    override val state: NavigationState,
    private val backing: Navigator = DefaultNavigator(state),
    private val pathOf: (NavKey) -> String,
    private val parsePath: (String) -> NavKey,
) : Navigator by backing {
    private val pathHistory = mutableListOf<String>()
    private var historyIndex = 0
    private var popStateListener: ((Event) -> Unit)? = null

    private fun currentLocation(): String =
        normalizeResourceLocation(window.location.pathname + window.location.search)

    private fun historyStatePayload(): String =
        encodeEntryStateSnapshot(state.entryStates.snapshot())

    private fun replaceState(path: String) =
        writeHistoryReplaceState(historyStatePayload(), path)

    private fun pushState(path: String) =
        writeHistoryPushState(historyStatePayload(), path)

    private fun applyHistoryEntry(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
            val stack = state.backStacks.getValue(route)
            while (stack.size > 1) stack.removeLastOrNull()
        } else {
            backing.popTo(route) ?: backing.navigate(route)
        }
    }

    fun attach() {
        if (popStateListener != null) throw IllegalStateException("Navigator is already attached")
        val stateExisted = hydrateEntryStatesFromHistory(state.entryStates)

        val listener: (Event) -> Unit = ::handlePopState
        popStateListener = listener
        window.addEventListener("popstate", listener)

        val initial = parsePath(currentLocation())
        applyHistoryEntry(initial)
        val path = pathOf(state.backStack.last())
        val startPath = normalizeResourceLocation(pathOf(state.startRoute))

        pathHistory.clear()
        pathHistory.add(path)
        historyIndex = 0

        if (!stateExisted && normalizeResourceLocation(path) != startPath) {
            replaceState(pathOf(state.startRoute))
            pathHistory[historyIndex] = startPath
            pushHistory(path)
        } else {
            replaceState(path)
        }
    }

    fun detach() {
        popStateListener?.let { window.removeEventListener("popstate", it) }
        popStateListener = null
    }

    @OptIn(FlowPreview::class)
    suspend fun syncEntryStates() {
        snapshotFlow { state.entryStates.snapshot() }
            .debounce(HISTORY_DEBOUNCE_MS.milliseconds)
            .collect {
                val path = pathOf(state.backStack.last())
                if (normalizeResourceLocation(path) == currentLocation()) {
                    replaceState(path)
                    pathHistory[historyIndex] = path
                }
            }
    }

    private fun handlePopState(event: Event) {
        state.entryStates.restore(decodeEntryStateSnapshot(readPopStatePayload(event)))
        applyHistoryEntry(parsePath(currentLocation()))
        syncHistoryIndexToCurrentPath()
    }

    private fun syncHistoryIndexToCurrentPath() {
        val path = currentLocation()
        val index = pathHistory.lastIndexOf(path)
        historyIndex = if (index >= 0) index else pathHistory.lastIndex.coerceAtLeast(0)
    }

    private fun pushHistory(path: String) {
        while (pathHistory.lastIndex > historyIndex) {
            pathHistory.removeLastOrNull()
        }
        historyIndex++
        pathHistory.add(path)
        pushState(path)
    }

    override fun navigate(route: NavKey) {
        backing.popTo(route) ?: backing.navigate(route)
        pushHistory(pathOf(state.backStack.last()))
    }

    override fun goBack() {
        if (!canGoBack()) return
        val startPath = normalizeResourceLocation(pathOf(state.startRoute))
        if (historyIndex > 0 || currentLocation() != startPath) {
            window.history.go(-1)
        } else {
            backing.goBack()
            replaceState(pathOf(state.backStack.last()))
        }
    }

    override fun popTo(route: NavKey): Unit? {
        val targetPath = pathOf(route)
        val index = pathHistory.lastIndexOf(targetPath)

        if (index in 0..<historyIndex) {
            window.history.go(index - historyIndex)
            return Unit
        }

        val result = backing.popTo(route)
        if (result == null) {
            backing.navigate(route)
        }
        pushHistory(pathOf(state.backStack.last()))
        return result
    }

    companion object {
        private const val HISTORY_DEBOUNCE_MS: Long = 200L
    }
}
