package dev.stashy.worker.internal

import kotlinx.coroutines.CancellationException

/**
 * Marks a cancellation of the job itself, as opposed to worker shutdown or a lost lease.
 */
internal class JobCancelledException : CancellationException("Job cancelled")
