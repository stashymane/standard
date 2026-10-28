package dev.stashy.standard.cron

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CronSerializationTest {
    @Serializable
    private data class Wrapper(val cron: Cron)

    @Test
    fun `serializes as a plain string`() {
        val expression = Cron.parse("0 0 3 ? * * *")
        val encoded = Json.encodeToString(Cron.serializer(), expression)

        assertEquals("\"0 0 3 ? * * *\"", encoded)
        assertEquals(
            expression,
            Json.decodeFromString(Cron.serializer(), encoded),
        )
    }

    @Test
    fun `serializes inside another value`() {
        val wrapper = Wrapper(Cron.parse("0 */15 9-17 ? * MON-FRI *"))
        val encoded = Json.encodeToString(Wrapper.serializer(), wrapper)

        assertEquals("{\"cron\":\"0 */15 9-17 ? * MON-FRI *\"}", encoded)
        assertEquals(wrapper, Json.decodeFromString(Wrapper.serializer(), encoded))
    }
}
