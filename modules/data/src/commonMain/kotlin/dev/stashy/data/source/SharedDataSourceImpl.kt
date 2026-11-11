package dev.stashy.data.source

import dev.stashy.data.DataSource
import dev.stashy.data.DataSourceState
import dev.stashy.data.SharedDataSource
import dev.stashy.data.toDataSourceState
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class SharedDataSourceImpl<T : Any>(
    private val source: DataSource<T>,
) : SharedDataSource<T> {
    override val state: StateFlow<DataSourceState<T>>
        field: MutableStateFlow<DataSourceState<T>> = MutableStateFlow(DataSourceState.Loading)

    override val isRefreshing: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val inFlight = atomic<CompletableDeferred<Result<T>>?>(null)

    override suspend fun await(): Result<T> {
        while (true) {
            when (val current = state.value) {
                is DataSourceState.Success -> return Result.success(current.value)
                else -> Unit
            }

            inFlight.value?.let { inFlight ->
                return inFlight.await()
            }

            val created = CompletableDeferred<Result<T>>()
            if (!inFlight.compareAndSet(null, created)) {
                continue
            }

            return runLoad(created)
        }
    }

    override suspend fun refresh(): Result<T> {
        while (true) {
            val existing = inFlight.value
            if (existing != null) {
                joinInFlight(existing)
                continue
            }

            val created = CompletableDeferred<Result<T>>()
            if (!inFlight.compareAndSet(null, created)) {
                continue
            }

            return runLoad(created)
        }
    }

    private suspend fun joinInFlight(current: CompletableDeferred<Result<T>>) {
        try {
            current.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }

    private suspend fun runLoad(created: CompletableDeferred<Result<T>>): Result<T> {
        isRefreshing.value = true

        return try {
            val result = source.await()
            state.value = result.toDataSourceState()
            created.complete(result)
            result
        } catch (e: CancellationException) {
            created.completeExceptionally(e)
            throw e
        } catch (e: Throwable) {
            val result = Result.failure<T>(e)
            state.value = DataSourceState.Failure(e)
            created.complete(result)
            result
        } finally {
            inFlight.compareAndSet(created, null)
            isRefreshing.value = false
        }
    }
}
