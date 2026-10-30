package dev.stashy.worker

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobQueue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal val TestJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal suspend fun <P : Any, C : Any> JobQueue<P>.create(
    job: Job,
    config: C,
    serializer: KSerializer<C>,
    scheduledAt: Instant,
): Boolean = create(job, codec.encode(config, serializer), scheduledAt)

internal suspend fun <P : Any, C : Any> JobQueue<P>.update(
    job: Job,
    config: C,
    serializer: KSerializer<C>,
    nextRunAt: Instant? = null,
): Boolean = update(job, codec.encode(config, serializer), nextRunAt)
