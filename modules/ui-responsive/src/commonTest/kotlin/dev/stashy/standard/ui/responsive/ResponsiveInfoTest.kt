package dev.stashy.standard.ui.responsive

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveInfoTest {
    @Test
    fun widthExtraSmallBreakpoint() {
        assertTrue(WidthSize(479) < ContainerSize.ExtraSmall)
        assertFalse(WidthSize(479) >= ContainerSize.ExtraSmall)
        assertTrue(WidthSize(480) >= ContainerSize.ExtraSmall)
        assertFalse(WidthSize(480) < ContainerSize.ExtraSmall)
    }

    @Test
    fun widthSmallBreakpoint() {
        assertTrue(WidthSize(599) < ContainerSize.Small)
        assertFalse(WidthSize(599) >= ContainerSize.Small)
        assertTrue(WidthSize(600) >= ContainerSize.Small)
        assertFalse(WidthSize(600) < ContainerSize.Small)
    }

    @Test
    fun widthMediumBreakpoint() {
        assertTrue(WidthSize(839) < ContainerSize.Medium)
        assertTrue(WidthSize(840) >= ContainerSize.Medium)
    }

    @Test
    fun widthLargeBreakpoint() {
        assertTrue(WidthSize(1199) < ContainerSize.Large)
        assertTrue(WidthSize(1200) >= ContainerSize.Large)
        assertTrue(WidthSize(1600) >= ContainerSize.Desktop)
    }

    @Test
    fun heightExtraSmallAndSmallShareBound() {
        assertTrue(HeightSize(479) < ContainerSize.ExtraSmall)
        assertTrue(HeightSize(479) < ContainerSize.Small)
        assertTrue(HeightSize(480) >= ContainerSize.ExtraSmall)
        assertTrue(HeightSize(480) >= ContainerSize.Small)
    }

    @Test
    fun heightMediumBreakpoint() {
        assertTrue(HeightSize(899) < ContainerSize.Medium)
        assertTrue(HeightSize(900) >= ContainerSize.Medium)
    }

    @Test
    fun heightLargeAndDesktopShareMediumBound() {
        assertTrue(HeightSize(899) < ContainerSize.Large)
        assertTrue(HeightSize(899) < ContainerSize.Desktop)
        assertTrue(HeightSize(900) >= ContainerSize.Large)
        assertTrue(HeightSize(900) >= ContainerSize.Desktop)
    }

    @Test
    fun sameNumericSizeDiffersByAxisAtSmall() {
        // Width Small is 600dp; height Small is 480dp.
        val size = 500
        assertTrue(WidthSize(size) < ContainerSize.Small)
        assertTrue(HeightSize(size) >= ContainerSize.Small)
    }

    @Test
    fun bothAxesComparisonRequiresBothAxes() {
        val bothSmall = ResponsiveInfo(WidthSize(600), HeightSize(480))
        assertTrue(bothSmall >= ContainerSize.Small)
        assertTrue(bothSmall >= ContainerSize.ExtraSmall)
        assertFalse(bothSmall >= ContainerSize.Medium)
        assertTrue(bothSmall < ContainerSize.Medium)

        val wideButShort = ResponsiveInfo(WidthSize(1200), HeightSize(400))
        assertTrue(wideButShort.width >= ContainerSize.Large)
        assertFalse(wideButShort.height >= ContainerSize.Small)
        assertFalse(wideButShort >= ContainerSize.Small)
        assertTrue(wideButShort < ContainerSize.Small)

        val tallButNarrow = ResponsiveInfo(WidthSize(500), HeightSize(900))
        assertFalse(tallButNarrow.width >= ContainerSize.Small)
        assertTrue(tallButNarrow.height >= ContainerSize.Medium)
        assertFalse(tallButNarrow >= ContainerSize.Small)

        val bothMedium = ResponsiveInfo(WidthSize(840), HeightSize(900))
        assertTrue(bothMedium >= ContainerSize.Medium)
        assertTrue(bothMedium >= ContainerSize.Small)
        assertFalse(bothMedium >= ContainerSize.Large)
    }
}
