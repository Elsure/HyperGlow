package com.elsure.hyperglow

import kotlin.math.PI
import kotlin.math.cos

/**
 * Drives an RGB output with a periodic waveform on a background thread (~25 fps).
 * The waveform maps a phase in [0,1) to a brightness factor in [0,1]; the base color is
 * scaled by that factor each frame. Backend-agnostic: [apply] can write root sysfs or the
 * binder privacy light.
 */
class LightAnimator(private val apply: (r: Int, g: Int, b: Int) -> Unit) {

    private var thread: Thread? = null

    @Volatile
    private var running = false

    fun start(baseR: Int, baseG: Int, baseB: Int, periodMs: Long, waveform: (Double) -> Double) {
        stop()
        running = true
        val safePeriod = periodMs.coerceAtLeast(100L)
        thread = Thread {
            val frameMs = 40L
            val start = System.currentTimeMillis()
            while (running) {
                val phase = ((System.currentTimeMillis() - start) % safePeriod).toDouble() / safePeriod
                val factor = waveform(phase).coerceIn(0.0, 1.0)
                apply(
                    (baseR * factor).toInt().coerceIn(0, 255),
                    (baseG * factor).toInt().coerceIn(0, 255),
                    (baseB * factor).toInt().coerceIn(0, 255),
                )
                try { Thread.sleep(frameMs) } catch (_: InterruptedException) { break }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    companion object {
        /** Smooth raised-cosine breath: 0 -> 1 -> 0 over one period. */
        val BREATHING: (Double) -> Double = { p -> (1.0 - cos(p * 2.0 * PI)) / 2.0 }

        /** Square-wave blink: on for the first half, off for the second. */
        val BLINK: (Double) -> Double = { p -> if (p < 0.5) 1.0 else 0.0 }
    }
}