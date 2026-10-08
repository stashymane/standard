package dev.stashy.data

import kotlin.coroutines.cancellation.CancellationException

internal fun <T> Result<T>.rethrowCancellation(): Result<T> = onFailure { if (it is CancellationException) throw it }
