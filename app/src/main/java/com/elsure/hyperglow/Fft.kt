package com.elsure.hyperglow

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Minimal in-place iterative radix-2 Cooley-Tukey FFT (no external deps). */
object Fft {

    /**
     * Complex FFT over [re]/[im] (length must be a power of two). After the call, bin k holds the
     * spectrum component at k * (sampleRate / n) Hz; magnitude = hypot(re[k], im[k]).
     */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        // bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len shr 1
                for (k in 0 until half) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                    val bIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + half] = aRe - bRe
                    im[i + k + half] = aIm - bIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Hann window coefficients for length [n]. */
    fun hann(n: Int): DoubleArray = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / (n - 1)) }
}