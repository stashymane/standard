package dev.stashy.standard.ui.responsive

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveInfoTest {
    @Test
    fun `width extra small breakpoint starts at 480`() {
        assertTrue(WidthSize(479) < ContainerSize.ExtraSmall)
        assertFalse(WidthSize(479) >= ContainerSize.ExtraSmall)
        assertTrue(WidthSize(480) >= ContainerSize.ExtraSmall)
        assertFalse(WidthSize(480) < ContainerSize.ExtraSmall)
    }

    @Test
    fun `width small breakpoint starts at 600`() {
        assertTrue(WidthSize(599) < ContainerSize.Small)
        assertFalse(WidthSize(599) >= ContainerSize.Small)
        assertTrue(WidthSize(600) >= ContainerSize.Small)
        assertFalse(WidthSize(600) < ContainerSize.Small)
    }

    @Test
    fun `width medium breakpoint starts at 840`() {
        assertTrue(WidthSize(839) < ContainerSize.Medium)
        assertTrue(WidthSize(840) >= ContainerSize.Medium)
    }

    @Test
    fun `width large and desktop start at 1200 and 1600`() {
        assertTrue(WidthSize(1199) < ContainerSize.Large)
        assertTrue(WidthSize(1200) >= ContainerSize.Large)
        assertTrue(WidthSize(1600) >= ContainerSize.Desktop)
    }

    @Test
    fun `height extra small and small share 480`() {
        assertTrue(HeightSize(479) < ContainerSize.ExtraSmall)
        assertTrue(HeightSize(479) < ContainerSize.Small)
        assertTrue(HeightSize(480) >= ContainerSize.ExtraSmall)
        assertTrue(HeightSize(480) >= ContainerSize.Small)
    }

    @Test
    fun `height medium breakpoint starts at 900`() {
        assertTrue(HeightSize(899) < ContainerSize.Medium)
        assertTrue(HeightSize(900) >= ContainerSize.Medium)
    }

    @Test
    fun `height large and desktop share 900`() {
        assertTrue(HeightSize(899) < ContainerSize.Large)
        assertTrue(HeightSize(899) < ContainerSize.Desktop)
        assertTrue(HeightSize(900) >= ContainerSize.Large)
        assertTrue(HeightSize(900) >= ContainerSize.Desktop)
    }

    @Test
    fun `same size differs by axis at small`() {
        // Width Small is 600dp; height Small is 480dp.
        val size = 500
        assertTrue(WidthSize(size) < ContainerSize.Small)
        assertTrue(HeightSize(size) >= ContainerSize.Small)
    }

    @Test
    fun `comparison requires both axes`() {
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
