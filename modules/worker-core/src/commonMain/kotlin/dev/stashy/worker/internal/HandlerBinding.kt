package dev.stashy.worker.internal

import dev.stashy.worker.handler.Handler
import dev.stashy.worker.store.JobQueue
import kotlinx.coroutines.CancellationException

internal class HandlerBinding<P : Any>(
    val id: String,
    private val prepareExecution: suspend (JobQueue<P>, JobQueue.Claimed<P>, Handler.Context) -> Handler.Prepared,
) {
    suspend fun prepare(
        store: JobQueue<P>,
        claim: JobQueue.Claimed<P>,
        context: Handler.Context,
    ): Handler.Prepared = prepareExecution(store, claim, context)
}

internal fun <P : Any, C : Any> handlerBinding(handler: Handler<C>): HandlerBinding<P> {
    val id = handler.configSerializer.descriptor.serialName
    return HandlerBinding(id) { store, claim, context ->
        val config = try {
            store.codec.decode(claim.job.payload, handler.configSerializer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw InvalidHandlerPayloadException(error)
        }
        handler.prepare(context, config)
    }
}
