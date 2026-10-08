package dev.stashy.data.source

import dev.stashy.data.DataSource
import kotlinx.coroutines.CancellationException
import kotlin.jvm.JvmInline

@JvmInline
value class SuspendingDataSource<T>(
    private val load: suspend () -> T,
) : DataSource<T> {
    override fun getOrNull(): T? = null

    override suspend fun await(): Result<T> = try {
        Result.success(load())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
