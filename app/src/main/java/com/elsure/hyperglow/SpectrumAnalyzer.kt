package com.elsure.hyperglow

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/**
 * Microphone-driven music spectrum -> RGB LED.
 *
 * Captures PCM16 mono (VOICE_RECOGNITION = least AGC/processing), runs a 1024-point Hann-windowed
 * FFT and reduces the spectrum to three per-bin-averaged bands:
 *   bass < 250 Hz, mid < 2000 Hz, treble < 8000 Hz.
 *
 * Color model (tuned so broadband sound is NOT just bright white):
 *  - hue   : each band relative to the current loudest band, raised to CONTRAST. The dominant band
 *            goes full while the others dim by their ratio, so the LED shows spectral balance.
 *  - level : current loudness vs a slow-decaying peak, above an adaptive noise floor, so silence
 *            and low hiss fade to dark instead of flickering.
 *  - fast-attack / slow-release smoothing steadies the output frame to frame.
 *
 * [sensitivity] scales how far toward the brightness ceiling a given loudness reaches. [maxLevel]
 * (default 0.16 = 16%) is a HARD brightness ceiling so the LED never glares; while sound is present
 * the level never drops below MIN_LEVEL (1%), and silence fades fully to dark.
 *
 * Resilience: if the mic is preempted by another app or read() errors, the capture loop releases
 * and re-acquires the recorder until [stop] is called, so the spectrum resumes once the mic frees.
 *
 * NOTE: uses the microphone, so it reacts to speaker/ambient playback; headphone playback is not
 * captured, and another app holding the mic will temporarily preempt this one.
 */
class SpectrumAnalyzer(
    private val onFrame: (r: Int, g: Int, b: Int) -> Unit,
    private val source: Source = Source.MIC,
    private val projection: MediaProjection? = null,
    private val onLevels: ((bass: Float, mid: Float, treble: Float) -> Unit)? = null,
) {

    /** Where the PCM comes from. */
    enum class Source {
        /** Microphone: picks up speaker output and ambient sound; headphones are NOT captured. */
        MIC,

        /** Screen-capture playback: the audio stream itself, so headphone output works too. */
        PLAYBACK,
    }


    companion object {
        private const val TAG = "MiuiLight"
        private const val SAMPLE_RATE = 22050
        private const val FFT_SIZE = 1024
        private const val BASS_MAX_HZ = 250.0
        private const val MID_MAX_HZ = 2000.0
        private const val TREBLE_MAX_HZ = 8000.0
        private const val CONTRAST = 2.0    // steepen band ratios -> more saturated color
        private const val GATE = 0.06       // per-channel floor (of the dominant band) -> zeroed
        private const val MIN_LEVEL = 0.01  // dimmest active brightness (1% of full scale)
        private const val BAND_DECAY = 0.995 // per-band peak decay (~3 s memory at 21 fps)
    }

    private var thread: Thread? = null

    @Volatile
    private var running = false

    /** How aggressively loudness drives toward the ceiling. Higher = reaches maxLevel sooner. */
    @Volatile
    var sensitivity: Float = 1.0f

    /** HARD brightness ceiling as a fraction of full scale (0..1). Default 16% -> never glares. */
    @Volatile
    var maxLevel: Float = 0.16f

    private var lastR = -1
    private var lastG = -1
    private var lastB = -1

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        running = true
        thread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (running) {
                val record = openRecorder()
                if (record == null) {
                    if (!sleep(800)) break
                    continue
                }
                var started = false
                try { record.startRecording(); started = true } catch (e: Exception) {
                    Log.w(TAG, "spectrum: startRecording failed: ${e.message}")
                }
                if (started) {
                    val clean = runLoop(record)
                    try { record.stop() } catch (_: Exception) {}
                    try { record.release() } catch (_: Exception) {}
                    // mic preempted/errored: wait for it to free up, then retry
                    if (!clean && running && !sleep(600)) break
                } else {
                    try { record.release() } catch (_: Exception) {}
                    if (!sleep(800)) break
                }
            }
        }.also { it.start() }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null
        val bufSize = max(minBuf, FFT_SIZE * 8)
        return try {
            val r = if (source == Source.PLAYBACK && projection != null) {
                // Capture the playback stream itself. Apps may opt out via
                // allowAudioPlaybackCapture="false" or by using non-capturable usages (e.g. calls,
                // some DRM streams) — those simply yield silence rather than an error.
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
            }
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); null } else r
        } catch (e: Exception) {
            Log.w(TAG, "spectrum: openRecorder failed: ${e.message}")
            null
        }
    }

    /** Process blocks until stopped or the mic fails. Returns false on a mic error/preemption. */
    private fun runLoop(record: AudioRecord): Boolean {
        val window = Fft.hann(FFT_SIZE)
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val pcm = ShortArray(FFT_SIZE)
        val hzPerBin = SAMPLE_RATE.toDouble() / FFT_SIZE
        val bassBins = (BASS_MAX_HZ / hzPerBin).toInt().coerceAtLeast(1)
        val midEnd = (MID_MAX_HZ / hzPerBin).toInt().coerceAtLeast(bassBins + 1)
        val trebleEnd = (TREBLE_MAX_HZ / hzPerBin).toInt().coerceAtLeast(midEnd + 1)
        val midBins = midEnd - bassBins
        val trebleBins = trebleEnd - midEnd
        var sR = 0.0; var sG = 0.0; var sB = 0.0   // smoothed per-bin band levels
        // Per-band adaptive references. Music is heavily low-tilted (roughly -3..-6 dB per octave),
        // so normalising all three bands against a SHARED maximum pins bass at 100% and starves
        // treble. Each band therefore tracks its own peak/floor and is scaled within its own
        // dynamic range, which is what makes mids and highs actually move.
        var pR = 1e-6; var pG = 1e-6; var pB = 1e-6   // per-band slow-decaying peaks
        var fR = 0.0; var fG = 0.0; var fB = 0.0      // per-band adaptive noise floors
        var peak = 1.0                              // slow-decaying loudness reference
        var floor = 0.0                             // adaptive noise floor
        var badReads = 0
        while (running) {
            if (record.recordingState == AudioRecord.RECORDSTATE_STOPPED) return false
            val read = record.read(pcm, 0, FFT_SIZE)
            if (read < 0) { if (++badReads > 5) return false; continue }
            if (read < FFT_SIZE) continue
            badReads = 0
            for (i in 0 until FFT_SIZE) {
                re[i] = (pcm[i].toInt() / 32768.0) * window[i]
                im[i] = 0.0
            }
            Fft.fft(re, im)
            var sr = 0.0; var sg = 0.0; var sb = 0.0
            val half = FFT_SIZE / 2
            for (k in 1 until half) {
                val mag = hypot(re[k], im[k])
                when {
                    k < bassBins -> sr += mag
                    k < midEnd -> sg += mag
                    k < trebleEnd -> sb += mag
                }
            }
            // per-bin average so a wide band does not dominate just by having more bins
            val bass = sr / bassBins
            val mid = sg / midBins
            val treb = sb / trebleBins
            sR = smooth(sR, bass); sG = smooth(sG, mid); sB = smooth(sB, treb)
            val mx = maxOf(sR, sG, sB)
            // overall loudness reference (drives brightness, not colour)
            floor += (mx - floor) * (if (mx < floor) 0.2 else 0.002)
            peak = max(peak * 0.985, mx)
            val base = floor * 1.3
            val denom = peak - base
            val loud = if (denom > 1e-6) ((mx - base) / denom).coerceIn(0.0, 1.0) else 0.0
            // sensitivity drives how far toward the brightness ceiling this frame reaches
            val drive = (loud * sensitivity.toDouble()).coerceIn(0.0, 1.0)

            // Track each band's own peak and floor, then express it inside its own range.
            pR = max(pR * BAND_DECAY, sR); fR += (sR - fR) * (if (sR < fR) 0.2 else 0.0015)
            pG = max(pG * BAND_DECAY, sG); fG += (sG - fG) * (if (sG < fG) 0.2 else 0.0015)
            pB = max(pB * BAND_DECAY, sB); fB += (sB - fB) * (if (sB < fB) 0.2 else 0.0015)
            val bR = bandNorm(sR, fR, pR)
            val bG = bandNorm(sG, fG, pG)
            val bB = bandNorm(sB, fB, pB)

            onLevels?.invoke(
                (bR * drive).toFloat().coerceIn(0f, 1f),
                (bG * drive).toFloat().coerceIn(0f, 1f),
                (bB * drive).toFloat().coerceIn(0f, 1f),
            )
            if (drive <= 0.02) { emit(0.0, 0.0, 0.0); continue }
            // hue from which band is most active RELATIVE TO ITSELF, so a cymbal can light blue
            // even though the kick drum carries far more raw energy
            val mxn = maxOf(bR, bG, bB)
            if (mxn <= 1e-6) { emit(0.0, 0.0, 0.0); continue }
            var nr = (bR / mxn).pow(CONTRAST)
            var ng = (bG / mxn).pow(CONTRAST)
            var nb = (bB / mxn).pow(CONTRAST)
            if (nr < GATE) nr = 0.0
            if (ng < GATE) ng = 0.0
            if (nb < GATE) nb = 0.0
            // map loudness onto [MIN_LEVEL, maxLevel]: hard-capped so the LED is never glaring
            val lo = MIN_LEVEL
            val hi = maxLevel.toDouble()
            val intensity = lo + (hi - lo) * drive
            emit(nr * intensity, ng * intensity, nb * intensity)
        }
        return true
    }

    /** Express a band's level inside its own [floor]..[peak] range. */
    private fun bandNorm(v: Double, floor: Double, peak: Double): Double {
        val lo = floor * 1.2
        val span = peak - lo
        return if (span > 1e-9) ((v - lo) / span).coerceIn(0.0, 1.0) else 0.0
    }

    /** Fast attack, slow release. */
    private fun smooth(prev: Double, cur: Double): Double =
        if (cur > prev) prev + (cur - prev) * 0.5 else prev + (cur - prev) * 0.15

    private fun emit(r: Double, g: Double, b: Double) {
        val ri = (r * 255.0).toInt().coerceIn(0, 255)
        val gi = (g * 255.0).toInt().coerceIn(0, 255)
        val bi = (b * 255.0).toInt().coerceIn(0, 255)
        if (ri == lastR && gi == lastG && bi == lastB) return
        lastR = ri; lastG = gi; lastB = bi
        try { onFrame(ri, gi, bi) } catch (_: Exception) {}
    }

    /** Sleep that returns false if interrupted (so the outer loop exits promptly on stop). */
    private fun sleep(ms: Long): Boolean =
        try { Thread.sleep(ms); true } catch (_: InterruptedException) { false }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        onLevels?.invoke(0f, 0f, 0f)
    }
}
