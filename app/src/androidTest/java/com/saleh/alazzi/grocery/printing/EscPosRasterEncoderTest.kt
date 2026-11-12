package com.saleh.alazzi.grocery.printing

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosRasterEncoderTest {
    @Test
    fun createsCorrectRasterBandHeadersFor58MillimeterWidth() {
        val bitmap = Bitmap.createBitmap(384, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(0, 0, Color.BLACK)

        val bands = EscPosRasterEncoder.encode(bitmap)

        assertEquals(2, bands.size)
        assertEquals(48, bands[0][4].toInt() and 0xFF)
        assertEquals(0, bands[0][5].toInt() and 0xFF)
        assertEquals(0, bands[0][6].toInt() and 0xFF)
        assertEquals(1, bands[0][7].toInt() and 0xFF)
        assertEquals(8 + 48 * 256, bands[0].size)
        assertEquals(8 + 48 * 44, bands[1].size)
        assertTrue((bands[0][8].toInt() and 0x80) != 0)
        bitmap.recycle()
    }
}
