package dev.stashy.standard.coroutine

/**
 * Runs the suspending [block] using [runCatching], automatically re-throwing any [CancellationExceptions][kotlin.coroutines.cancellation.CancellationException].
 */
public suspend inline fun <R> runCatchingSuspend(
    block: suspend () -> R
): Result<R> = runCatching {
    block()
}.rethrowCancellation()
