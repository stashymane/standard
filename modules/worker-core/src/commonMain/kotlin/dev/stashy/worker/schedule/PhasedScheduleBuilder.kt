package dev.stashy.worker.schedule

import dev.stashy.worker.JobsDsl
import kotlin.time.Instant

/**
 * Builds a [Schedule.Phased] out of consecutive segments.
 */
@JobsDsl
public class PhasedScheduleBuilder internal constructor() {
    private val phases = mutableListOf<Schedule.Phased.Phase>()
    private var start: Instant? = null
    private var finished = false

    /**
     * Schedules [configure] from the previous segment until [at].
     */
    public fun until(at: Instant, configure: ScheduleDsl.() -> Schedule) {
        require(!finished) { "then must be the last segment" }
        val previous = start
        require(previous == null || at > previous) { "until instants must increase" }
        add(configure)
        start = at
    }

    /**
     * Schedules [configure] from the last `until` instant onwards, without an end.
     */
    public fun then(configure: ScheduleDsl.() -> Schedule) {
        require(!finished) { "then must be the last segment" }
        add(configure)
        finished = true
    }

    internal fun build(): Schedule.Phased {
        require(phases.isNotEmpty()) { "phased requires at least one segment" }
        require(finished) { "phased must end with a then segment" }
        return Schedule.Phased(phases.toList())
    }

    private fun add(configure: ScheduleDsl.() -> Schedule) {
        phases += Schedule.Phased.Phase(start, ScheduleDsl.configure())
    }
}
