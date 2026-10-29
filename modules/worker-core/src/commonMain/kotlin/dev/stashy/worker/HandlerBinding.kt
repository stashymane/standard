package dev.stashy.worker

import dev.stashy.worker.job.JobStore
import kotlinx.coroutines.CancellationException

internal class HandlerBinding(
    val id: String,
    private val prepareExecution: suspend (JobStore, JobStore.Claimed, Handler.Context) -> Handler.Prepared,
) {
    suspend fun prepare(
        store: JobStore,
        claim: JobStore.Claimed,
        context: Handler.Context,
    ): Handler.Prepared = prepareExecution(store, claim, context)
}

internal fun <C : Any> handlerBinding(handler: Handler<C>): HandlerBinding {
    val id = handler.configSerializer.descriptor.serialName
    return HandlerBinding(id) { store, claim, context ->
        val config = try {
            store.readConfig(claim, handler.configSerializer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw InvalidHandlerPayloadException(error)
        }
        handler.prepare(context, config)
    }
}
