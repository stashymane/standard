package dev.stashy.data.source

import dev.stashy.data.DataSource
import dev.stashy.standard.coroutine.runCatchingSuspend
import kotlin.jvm.JvmInline

@JvmInline
internal value class SuspendingDataSource<T : Any>(
    private val load: suspend () -> T,
) : DataSource<T> {
    override fun getOrNull(): T? = null

    override suspend fun await(): Result<T> = runCatchingSuspend {
        load()
    }
}
