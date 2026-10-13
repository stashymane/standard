package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Updates [this] stack the way browser history would: pop back to [entry] if it is already
 * present, otherwise push it.
 *
 * Uses [add] and [removeAt] on the list. For [GroupBackStack], prefer [GroupBackStack.syncTo] or pass
 * [GroupBackStack.flattened] with an [applyEntry] callback to browser sync.
 */
@InternalNavApi
public fun <Entry : NavKey> MutableList<Entry>.syncTo(entry: Entry) {
    if (isEmpty()) {
        add(entry)
        return
    }
    if (last() == entry) return

    val index = lastIndexOf(entry)
    if (index >= 0) {
        while (lastIndex > index) {
            removeAt(lastIndex)
        }
    } else {
        add(entry)
    }
}
