@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.stashy.navigation

import org.w3c.dom.events.Event

@JsFun("(data, url) => history.replaceState(data, '', url)")
private external fun jsReplaceHistoryState(data: String, url: String)

@JsFun("(data, url) => history.pushState(data, '', url)")
private external fun jsPushHistoryState(data: String, url: String)

@JsFun("(event) => { const value = event.state; return value == null ? null : String(value); }")
private external fun jsPopStateEventState(event: Event): String?

@JsFun("() => { const value = history.state; return value == null ? null : String(value); }")
private external fun jsCurrentHistoryState(): String?

internal fun readPopStatePayload(event: Event): Any? = jsPopStateEventState(event)

internal fun writeHistoryReplaceState(stateJson: String, path: String) {
    jsReplaceHistoryState(stateJson, path)
}

internal fun writeHistoryPushState(stateJson: String, path: String) {
    jsPushHistoryState(stateJson, path)
}

internal fun readCurrentHistoryState(): Any? = jsCurrentHistoryState()
