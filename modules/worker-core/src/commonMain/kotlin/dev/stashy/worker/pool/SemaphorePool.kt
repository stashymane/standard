package dev.stashy.worker.pool

import kotlinx.coroutines.sync.Semaphore

internal class SemaphorePool(
    override val label: String?,
    override val capacity: Int,
) : ConcurrencyPool {
    private val permits = Semaphore(capacity)

    override suspend fun admit(request: PoolRequest): Int {
        val want = request.want.coerceIn(1, capacity)
        permits.acquire()
        var granted = 1
        while (granted < want && permits.tryAcquire()) granted++
        return granted
    }

    override fun release(count: Int) {
        repeat(count) { permits.release() }
    }
}
