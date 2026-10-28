package dev.stashy.standard.cron

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CronParsingTest {
    @Test
    fun `parses a daily expression`() {
        assertEquals(
            "0 0 3 ? * * *",
            Cron.parse("0 0 3 ? * * *").expression,
        )
    }

    @Test
    fun `parses ranges, names, and lists`() {
        assertEquals(
            "0 0 9 ? * MON,WED-FRI *",
            Cron.parse("0 0 9 ? * MON,WED-FRI *").expression,
        )
        assertEquals(
            "0 0 0 1 JAN-MAR ? *",
            Cron.parse("0 0 0 1 JAN-MAR ? *").expression,
        )
    }

    @Test
    fun `parses steps`() {
        assertEquals(
            "0 */15 * ? * * *",
            Cron.parse("0 */15 * ? * * *").expression,
        )
        assertEquals(
            "0 5-45/10 * ? * * *",
            Cron.parse("0 5-45/10 * ? * * *").expression,
        )
    }

    @Test
    fun `parses day-of-month specials`() {
        assertEquals("0 0 0 L * ? *", Cron.parse("0 0 0 L * ? *").expression)
        assertEquals("0 0 0 LW * ? *", Cron.parse("0 0 0 LW * ? *").expression)
        assertEquals("0 0 0 L-3 * ? *", Cron.parse("0 0 0 L-3 * ? *").expression)
        assertEquals("0 0 0 15W * ? *", Cron.parse("0 0 0 15W * ? *").expression)
    }

    @Test
    fun `parses day-of-week specials`() {
        assertEquals("0 0 0 ? * MON#1 *", Cron.parse("0 0 0 ? * 2#1 *").expression)
        assertEquals("0 0 0 ? * FRIL *", Cron.parse("0 0 0 ? * 6L *").expression)
        assertEquals("0 0 0 ? * SUNL *", Cron.parse("0 0 0 ? * SUNL *").expression)
    }

    @Test
    fun `numeric day-of-week follows cron convention`() {
        assertEquals("0 0 0 ? * SUN *", Cron.parse("0 0 0 ? * 1 *").expression)
        assertEquals("0 0 0 ? * SAT *", Cron.parse("0 0 0 ? * 7 *").expression)
        assertEquals(
            Cron.parse("0 0 0 ? * SUN *"),
            Cron.parse("0 0 0 ? * 1 *"),
        )
    }

    @Test
    fun `day-of-week lists keep authored order`() {
        assertEquals(
            "0 0 0 ? * MON,WED,FRI,SUN *",
            Cron.parse("0 0 0 ? * MON,WED,FRI,SUN *").expression,
        )
        assertEquals(
            "0 0 0 ? * */2 *",
            Cron.parse("0 0 0 ? * */2 *").expression,
        )
    }

    @Test
    fun `preserves authored segments`() {
        assertEquals(
            "0 0 0 ? * MON,MON,TUE *",
            Cron.parse("0 0 0 ? * MON,MON,TUE *").expression,
        )
    }

    @Test
    fun `unrestricted day fields normalize to a single form`() {
        assertEquals("0 0 0 ? * * *", Cron.parse("0 0 0 * * * *").expression)
        assertEquals("0 0 0 ? * * *", Cron.parse("0 0 0 1-31 * 1-7 *").expression)
    }

    @Test
    fun `supports years beyond the previous upper bound`() {
        val expression = Cron.parse("0 0 0 1 JAN ? 2100")

        assertTrue(expression.matches(LocalDateTime.parse("2100-01-01T00:00:00")))
    }

    @Test
    fun `rejects classic and six-field expressions`() {
        assertFailsWith<CronParseException> { Cron.parse("0 3 * * *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 3 ? * *") }
    }

    @Test
    fun `rejects both restricted day fields`() {
        assertFailsWith<CronParseException> {
            Cron.parse("0 0 0 1 * MON *")
        }
    }

    @Test
    fun `rejects invalid fields and values`() {
        assertFailsWith<CronParseException> { Cron.parse("60 0 0 ? * * *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 24 ? * * *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 32 * ? *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? 13 * *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * 0 *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * 8 *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * * -1000000000") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * * 1000000000") }
        assertFailsWith<CronParseException> { Cron.parse("? 0 0 ? * * *") }
    }

    @Test
    fun `rejects specials in lists`() {
        assertFailsWith<CronParseException> {
            Cron.parse("0 0 0 L,1 * ? *")
        }
        assertFailsWith<CronParseException> {
            Cron.parse("0 0 0 ? * MONL,TUE *")
        }
    }

    @Test
    fun `rejects malformed specials`() {
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * L *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * MON#0 *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 ? * MON#6 *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 L-31 * ? *") }
        assertFailsWith<CronParseException> { Cron.parse("0 0 0 0W * ? *") }
    }

    @Test
    fun `expressions round trip`() {
        val expressions = listOf(
            "0 0 3 ? * * *",
            "*/15 * * ? * * *",
            "0 0 9 ? * MON-FRI *",
            "0 0 0 1 JAN-MAR ? *",
            "0 0 0 L * ? *",
            "0 0 0 15W * ? *",
            "0 0 0 ? * MON#1 *",
            "0 0 0 ? * FRIL *",
            "0 0 0 ? * TUE-SAT/2,SUN *",
            "0 0 0 1 * ? 2027",
        )

        for (value in expressions) {
            val parsed = Cron.parse(value)
            assertEquals(parsed, Cron.parse(parsed.expression))
        }
    }
}
