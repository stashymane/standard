package dev.stashy.standard.cron.fields

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentTest {
    @Test
    fun `checks membership in a stepped segment`() {
        val segment = Segment(5, 45, 10)

        assertTrue(5 in segment)
        assertFalse(6 in segment)
        assertTrue(15 in segment)
        assertTrue(45 in segment)
        assertFalse(46 in segment)
    }

    @Test
    fun `finds the next value in a stepped segment`() {
        val segment = Segment(5, 45, 10)

        assertEquals(5, segment.nextOrNull(4))
        assertEquals(15, segment.nextOrNull(5))
        assertEquals(15, segment.nextOrNull(6))
        assertEquals(45, segment.nextOrNull(44))
        assertNull(segment.nextOrNull(45))
    }

    @Test
    fun `finds the next value in a single-value segment`() {
        val segment = Segment(7, 7)

        assertEquals(7, segment.nextOrNull(0))
        assertNull(segment.nextOrNull(7))
    }

    @Test
    fun `handles steps larger than the segment`() {
        val segment = Segment(5, 45, Int.MAX_VALUE)

        assertEquals(5, segment.nextOrNull(4))
        assertNull(segment.nextOrNull(5))
    }
}
