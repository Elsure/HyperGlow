package com.elsure.hyperglow

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure color-math tests for the ViewModel's brightness scaling / ARGB packing. */
class ColorTest {

    @Test
    fun fullBrightnessKeepsChannels() {
        val c = composeColor(255, 128, 0, 100)
        assertEquals(0xFF, (c ushr 24) and 0xFF)   // opaque alpha
        assertEquals(255, (c ushr 16) and 0xFF)
        assertEquals(128, (c ushr 8) and 0xFF)
        assertEquals(0, c and 0xFF)
    }

    @Test
    fun halfBrightnessHalvesChannels() {
        val c = composeColor(200, 100, 50, 50)
        assertEquals(100, (c ushr 16) and 0xFF)
        assertEquals(50, (c ushr 8) and 0xFF)
        assertEquals(25, c and 0xFF)
    }

    @Test
    fun scaleChannelClampsTo255() {
        assertEquals(255, scaleChannel(255, 200))  // >100% cannot overflow the 0..255 node range
        assertEquals(0, scaleChannel(0, 100))
    }
}
