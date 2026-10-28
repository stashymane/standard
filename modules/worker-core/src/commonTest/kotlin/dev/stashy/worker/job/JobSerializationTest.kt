package dev.stashy.worker.job

import dev.stashy.worker.TestJson
import dev.stashy.worker.schedule.Schedule
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class JobSerializationTest {
    @Test
    fun `cron schedule serializes its expression and time zone separately`() {
        val definition = Job.Definition(
            id = Job.Id("nightly"),
            handlerId = "cleanup",
            schedule = Schedule.Cron("0 0 3 ? * * *", TimeZone.UTC.id),
        )

        val encoded = TestJson.encodeToString(definition)

        assertTrue(encoded.contains("\"expression\":\"0 0 3 ? * * *\""))
        assertTrue(encoded.contains("\"timeZoneId\":\"UTC\""))
    }

    @Test
    fun `job definitions round trip through json`() {
        val schedules = listOf(
            Schedule.Once(Instant.parse("2026-10-27T12:00:00Z")),
            Schedule.FixedRate(1.hours, Instant.parse("2026-10-27T12:00:00Z")),
            Schedule.FixedRate(1.hours),
            Schedule.FixedDelay(10.seconds),
            Schedule.Cron("0 0 3 ? * * *", TimeZone.UTC.id),
        )

        for (schedule in schedules) {
            val definition = Job.Definition(
                id = Job.Id("nightly"),
                handlerId = "cleanup",
                schedule = schedule,
                latePolicy = LateExecutionPolicy.FailAfter(5.seconds),
            )

            val decoded = TestJson.decodeFromString<Job.Definition>(
                TestJson.encodeToString(definition),
            )

            assertEquals(definition, decoded)
            assertEquals(schedule::class, decoded.schedule::class)
        }
    }
}
