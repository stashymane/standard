package dev.stashy.worker

import dev.stashy.worker.job.JobStore
import kotlinx.coroutines.CancellationException

internal class HandlerBinding(
    val id: String,
    private val prepareExecution: suspend (JobStore, JobStore.Claimed) -> suspend (Handler.Context) -> Unit,
) {
    suspend fun prepare(store: JobStore, claim: JobStore.Claimed): suspend (Handler.Context) -> Unit =
        prepareExecution(store, claim)
}

internal fun <C : Any> handlerBinding(handler: Handler<C>): HandlerBinding {
    val id = handler.configSerializer.descriptor.serialName
    return HandlerBinding(id) { store, claim ->
        val config = try {
            store.readConfig(claim, handler.configSerializer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw InvalidHandlerPayloadException(error)
        }
        { context -> handler.execute(context, config) }
    }
}
