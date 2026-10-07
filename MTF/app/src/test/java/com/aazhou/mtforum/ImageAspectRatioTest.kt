package com.aazhou.mtforum

import com.aazhou.mtforum.image.imageAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageAspectRatioTest {

    @Test
    fun landscapeKeepsNativeRatio() {
        assertEquals(2000f / 300f, imageAspectRatio(2000, 300)!!, 0.001f)
        assertEquals(1.25f, imageAspectRatio(1000, 800)!!, 0.001f)
    }

    @Test
    fun portraitIsClampedToFloor() {
        // 竖长图钳到下限 0.8：图片盒最高占 1.25 倍宽，竖图不占满多屏
        assertEquals(0.8f, imageAspectRatio(600, 1200)!!, 0.001f)
        assertEquals(1f, imageAspectRatio(1000, 1000)!!, 0.001f)
    }

    @Test
    fun invalidSizeReturnsNull() {
        assertNull(imageAspectRatio(0, 100))
        assertNull(imageAspectRatio(100, 0))
        assertNull(imageAspectRatio(-1, 50))
    }
}
