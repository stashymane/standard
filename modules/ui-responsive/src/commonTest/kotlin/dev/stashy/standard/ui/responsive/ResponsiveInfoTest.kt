package dev.stashy.standard.ui.responsive

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveInfoTest {
    @Test
    fun widthMediumBreakpoint() {
        assertTrue(WidthSize(599) < ContainerSize.Medium)
        assertFalse(WidthSize(599) >= ContainerSize.Medium)
        assertTrue(WidthSize(600) >= ContainerSize.Medium)
        assertFalse(WidthSize(600) < ContainerSize.Medium)
    }

    @Test
    fun widthLargeBreakpoint() {
        assertTrue(WidthSize(1199) < ContainerSize.Large)
        assertTrue(WidthSize(1200) >= ContainerSize.Large)
        assertTrue(WidthSize(1600) >= ContainerSize.Desktop)
    }

    @Test
    fun heightMediumBreakpoint() {
        assertTrue(HeightSize(479) < ContainerSize.Medium)
        assertFalse(HeightSize(479) >= ContainerSize.Medium)
        assertTrue(HeightSize(480) >= ContainerSize.Medium)
        assertFalse(HeightSize(480) < ContainerSize.Medium)
    }

    @Test
    fun heightExpandedBreakpoint() {
        assertTrue(HeightSize(899) < ContainerSize.Expanded)
        assertTrue(HeightSize(900) >= ContainerSize.Expanded)
    }

    @Test
    fun heightLargeAndExtraLargeShareExpandedBound() {
        assertTrue(HeightSize(899) < ContainerSize.Large)
        assertTrue(HeightSize(899) < ContainerSize.Desktop)
        assertTrue(HeightSize(900) >= ContainerSize.Large)
        assertTrue(HeightSize(900) >= ContainerSize.Desktop)
    }

    @Test
    fun sameNumericSizeDiffersByAxisAtMedium() {
        // Width Medium is 600dp; height Medium is 480dp.
        val size = 500
        assertTrue(WidthSize(size) < ContainerSize.Medium)
        assertTrue(HeightSize(size) >= ContainerSize.Medium)
    }

    @Test
    fun compactIsAlwaysAtLeast() {
        assertTrue(WidthSize(0) >= ContainerSize.Compact)
        assertTrue(HeightSize(0) >= ContainerSize.Compact)
        assertTrue(WidthSize(100) >= ContainerSize.Compact)
        assertTrue(HeightSize(100) >= ContainerSize.Compact)
    }

    @Test
    fun bothAxesComparisonRequiresBothAxes() {
        val bothMedium = ResponsiveInfo(WidthSize(600), HeightSize(480))
        assertTrue(bothMedium >= ContainerSize.Medium)
        assertTrue(bothMedium >= ContainerSize.Compact)
        assertFalse(bothMedium >= ContainerSize.Expanded)
        assertTrue(bothMedium < ContainerSize.Expanded)

        val wideButShort = ResponsiveInfo(WidthSize(1200), HeightSize(400))
        assertTrue(wideButShort.width >= ContainerSize.Large)
        assertFalse(wideButShort.height >= ContainerSize.Medium)
        assertFalse(wideButShort >= ContainerSize.Medium)
        assertTrue(wideButShort < ContainerSize.Medium)

        val tallButNarrow = ResponsiveInfo(WidthSize(500), HeightSize(900))
        assertFalse(tallButNarrow.width >= ContainerSize.Medium)
        assertTrue(tallButNarrow.height >= ContainerSize.Expanded)
        assertFalse(tallButNarrow >= ContainerSize.Medium)

        val bothExpanded = ResponsiveInfo(WidthSize(840), HeightSize(900))
        assertTrue(bothExpanded >= ContainerSize.Expanded)
        assertTrue(bothExpanded >= ContainerSize.Medium)
    }
}
