package com.elsure.hyperglow

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/** The FFT is the heart of the music spectrum; verify it localizes a pure tone and windows cleanly. */
class FftTest {

    @Test
    fun peakLandsOnInputFrequencyBin() {
        val n = 16
        val k = 3
        val re = DoubleArray(n) { cos(2.0 * PI * k * it / n) }
        val im = DoubleArray(n)
        Fft.fft(re, im)
        val mag = DoubleArray(n) { hypot(re[it], im[it]) }
        var best = 1
        for (i in 1..n / 2) if (mag[i] > mag[best]) best = i
        assertEquals(k, best)
    }

    @Test
    fun hannWindowEndpointsAreZero() {
        val w = Fft.hann(8)
        assertEquals(0.0, w[0], 1e-9)
        assertEquals(0.0, w[7], 1e-9)
    }
}
