package dev.stashy.standard.coroutine

import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-throws [CancellationException]s.
 *
 * [runCatching] swallows all exceptions, including cancellation, which may break proper coroutine execution.
 */
public fun <T> Result<T>.rethrowCancellation(): Result<T> = onFailure { exception ->
    if (exception is CancellationException)
        throw exception
}
