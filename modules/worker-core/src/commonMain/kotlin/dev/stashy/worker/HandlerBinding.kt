package dev.stashy.worker

import dev.stashy.worker.job.JobQueue
import kotlinx.coroutines.CancellationException

internal class HandlerBinding(
    val id: String,
    private val prepareExecution: suspend (JobQueue, JobQueue.Claimed, Handler.Context) -> Handler.Prepared,
) {
    suspend fun prepare(
        store: JobQueue,
        claim: JobQueue.Claimed,
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
