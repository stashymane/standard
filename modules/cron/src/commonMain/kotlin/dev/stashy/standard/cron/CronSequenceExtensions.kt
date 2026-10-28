package dev.stashy.standard.cron

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Lazily yields matching wall-clock times starting strictly after [from].
 */
public fun Cron.asSequence(from: LocalDateTime): Sequence<LocalDateTime> =
    generateSequence(next(from)) { next(it) }

/**
 * Lazily yields matching instants starting strictly after [from] in [timeZone].
 */
public fun Cron.asSequence(from: Instant, timeZone: TimeZone): Sequence<Instant> =
    generateSequence(next(from, timeZone)) { next(it, timeZone) }
