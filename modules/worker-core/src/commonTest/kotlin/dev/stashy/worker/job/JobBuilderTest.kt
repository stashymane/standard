package dev.stashy.worker.job

import dev.stashy.worker.schedule.Schedule
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class JobBuilderTest {
    private val boundary = Instant.parse("2026-10-27T12:00:00Z")

    @Test
    fun `a job without a schedule is rejected`() {
        val error = assertFailsWith<IllegalStateException> {
            defineJob("handler", Job.Id("job")) { retry = RetryPolicy.Instant() }
        }

        assertEquals("Job \"job\" has no schedule", error.message)
    }

    @Test
    fun `a job can be built from a phased schedule`() {
        val job = defineJob("handler", Job.Id("job")) {
            schedule = phased {
                until(boundary) { every(1.hours) }
                then { cron("0 0 3 ? * * *", TimeZone.UTC) }
            }
        }

        assertEquals(
            Schedule.Phased(
                listOf(
                    Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
                    Schedule.Phased.Phase(boundary, Schedule.Cron("0 0 3 ? * * *", TimeZone.UTC.id)),
                ),
            ),
            job.schedule,
        )
    }

    @Test
    fun `a job can be built from a leaf schedule`() {
        val job = defineJob("handler", Job.Id("job")) {
            schedule = every(10.minutes)
        }

        assertEquals(Schedule.FixedRate(10.minutes), job.schedule)
    }
}
