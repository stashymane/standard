package dev.stashy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
actual fun <Entry : MultiBackStack.Entry<Group>, Group : Any> SyncBrowserHistory(
    backStack: MultiBackStack<Entry, Group>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
) {
    val session = remember(backStack, pathOf, parsePath) {
        BrowserHistorySession(backStack, pathOf, parsePath)
    }

    LaunchedEffect(session) {
        session.bind()
    }

    DisposableEffect(session) {
        session.attachPopStateListener()
        onDispose { session.detachPopStateListener() }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private class BrowserHistorySession<Entry : MultiBackStack.Entry<Group>, Group : Any>(
    private val backStack: MultiBackStack<Entry, Group>,
    private val pathOf: (Entry) -> String,
    private val parsePath: (String) -> Entry,
) {
    /** How many `pushState` entries this session owns above the initial replaceState. */
    private var navigableDepth = 0
    private var lastSize = 1
    private var suppressPopState = false
    private var popStateListener: ((Event) -> Unit)? = null

    suspend fun bind() {
        val initial = parsePath(window.location.pathname)
        backStack.syncTo(initial)
        window.history.replaceState(null, "", pathOf(initial))
        navigableDepth = 0
        lastSize = backStack.backStack.size

        snapshotFlow { backStack.backStack.toList() }
            .collect { stack -> syncStackToHistory(stack) }
    }

    private fun syncStackToHistory(stack: List<Entry>) {
        val path = pathOf(stack.last())
        val locationPath = normalizePath(window.location.pathname)

        if (path == locationPath) {
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
                lastSize = backStack.backStack.size
            } else {
                val path = normalizePath(window.location.pathname)
                val sizeBefore = backStack.backStack.size
                backStack.syncTo(parsePath(path))
                val sizeAfter = backStack.backStack.size
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
