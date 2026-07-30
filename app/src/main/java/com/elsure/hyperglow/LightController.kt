package com.elsure.hyperglow

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.media.projection.MediaProjection
import android.provider.Settings

///**
// * Front-facing controller. Routes color/animation output to the best available backend:
// *  - ROOT/daemon   : full takeover. miuilightd re-writes /sys/class/leds/* as root every frame,
// *                    beating the camera/battery/notification override and surviving app freezing.
// *  - ROOT/sysfs    : direct writes via a long-lived root shell (foreground, no takeover).
// *  - Binder/privacy: the no-root fallback (setColorCommon styleType=7), subject to the camera
// *                    lifecycle override.
// *
// * Also coordinates with the in-app Xposed module (LightHook) through Settings.Global:
// *  - miuilight_takeover       : daemon/hook kill switch for the stock policy.
// *  - miuilight_privacy_mode   : 0 stock, 1 custom color, 2 hide (hook rewrites/blocks at source).
// *  - miuilight_privacy_color  : ARGB color used when privacy_mode == 1.
// *  - miuilight_evt_seq/_last  : light events mirrored by the hook, read here for monitoring.
// */
object LightController {

    const val PRIV_STOCK = 0
    const val PRIV_CUSTOM = 1
    const val PRIV_HIDE = 2

    private const val K_PRIV_MODE = "miuilight_privacy_mode"
    private const val K_PRIV_COLOR = "miuilight_privacy_color"
    private const val K_EVT_SEQ = "miuilight_evt_seq"
    private const val K_EVT_LAST = "miuilight_evt_last"

    private val binder = MiuiLightController()
    private val root = RootLightController()
    private val daemon = DaemonController()
    private var animator: LightAnimator? = null
    private var appContext: Context? = null
    private var spectrum: SpectrumAnalyzer? = null

    @Volatile
    var spectrumActive = false
        private set

    /** Audio source of the running spectrum (mic vs screen-recording playback capture). */
    @Volatile
    var spectrumSource: SpectrumAnalyzer.Source = SpectrumAnalyzer.Source.MIC
        private set

    /** Latest normalised band levels (bass, mid, treble) in 0..1, for the UI visualiser. */
    @Volatile
    var bands: Triple<Float, Float, Float> = Triple(0f, 0f, 0f)
        private set

    private var projection: MediaProjection? = null

    // State saved when spectrum starts, restored when it stops.
    private var savedTakeover = false
    private var savedMode = 1
    private var savedR = 0
    private var savedG = 0
    private var savedB = 0
    private var savedPeriod = 2000

    /**
     * Global brightness ceiling (0..1) applied to every LED output path. This is the app-wide
     * "how bright may the LED ever get" control; it is deliberately separate from colour, which
     * only decides hue/saturation.
     */
    @Volatile
    var brightnessCap: Float = 1.0f

    private fun cap(v: Int): Int = (v * brightnessCap).toInt().coerceIn(0, 255)

    @Volatile
    var rootReady = false
        private set

    @Volatile
    var takeoverActive = false
        private set

    // Desired state mirrored to the daemon (takeover,mode,R,G,B,period).
    private var dMode = 1   // 0 off, 1 solid, 2 breath, 3 blink
    private var dR = 0
    private var dG = 0
    private var dB = 0
    private var dPeriod = 2000

    fun init(context: Context) {
        appContext = context.applicationContext
        binder.init()
        daemon.init(context)
    }

    fun connectRoot(onDone: (Boolean) -> Unit) {
        Thread {
            val ok = try {
                RootLightController.isRootAvailable() && root.acquire()
            } catch (e: Exception) {
                false
            }
            rootReady = ok
            onDone(ok)
        }.start()
    }

    fun disconnectRoot() {
        stopAnimation()
        root.release()
        rootReady = false
    }

    private fun applyRgb(r: Int, g: Int, b: Int) {
        val cr = cap(r); val cg = cap(g); val cb = cap(b)
        // The actual LED write (binder transact or root sysfs echo) is off the calling thread so a
        // main-thread caller (e.g. TriggerService.evaluate -> applyTriggerEffect) can never stall
        // the UI. Desired-state fields above are already updated synchronously by the callers.
        Thread {
            if (rootReady && root.ready) root.setRgb(cr, cg, cb) else binder.setColor(pack(cr, cg, cb))
        }.start()
    }

    fun setColor(color: Int) {
        stopSpectrum()
        stopAnimation()
        val (r, g, b) = unpack(color)
        dMode = 1; dR = r; dG = g; dB = b
        if (takeoverActive) pushDaemon() else applyRgb(r, g, b)
    }

    fun off() {
        stopSpectrum()
        stopAnimation()
        dMode = 0; dR = 0; dG = 0; dB = 0
        if (takeoverActive) pushDaemon() else applyRgb(0, 0, 0)
    }

    fun startBreathing(color: Int, periodMs: Long) = startAnim(color, periodMs, LightAnimator.BREATHING, 2)
    fun startBlink(color: Int, periodMs: Long) = startAnim(color, periodMs, LightAnimator.BLINK, 3)

    private fun startAnim(color: Int, periodMs: Long, waveform: (Double) -> Double, modeCode: Int) {
        stopSpectrum()
        stopAnimation()
        val (r, g, b) = unpack(color)
        dMode = modeCode; dR = r; dG = g; dB = b; dPeriod = periodMs.toInt()
        if (takeoverActive) {
            pushDaemon()   // daemon animates + enforces, survives background freeze
        } else {
            animator = LightAnimator { rr, gg, bb -> applyRgb(rr, gg, bb) }
                .also { it.start(r, g, b, periodMs, waveform) }
        }
        syncForeground()
    }

    fun stopAnimation() {
        animator?.stop()
        animator = null
        syncForeground()
    }

    // ---- Music spectrum (microphone -> FFT -> RGB, driven through the root daemon) ----

    /**
     * Drive the LED from the microphone spectrum. Requires root: the daemon is installed/started
     * and put in takeover so it both renders the fast color frames and beats the camera override.
     * The previous color/animation + takeover state is remembered and restored by [stopSpectrum].
     */
    fun startSpectrum(
        sensitivity: Float = 1.0f,
        maxLevel: Float = 0.16f,
        source: SpectrumAnalyzer.Source = SpectrumAnalyzer.Source.MIC,
        mediaProjection: MediaProjection? = null,
    ): Boolean {
        if (spectrumActive) return true
        if (!RootLightController.isRootAvailable()) return false
        if (source == SpectrumAnalyzer.Source.PLAYBACK && mediaProjection == null) return false
        if (!daemonRunning()) {
            if (!(daemon.install() && daemon.start())) return false
        }
        savedTakeover = takeoverActive
        savedMode = dMode; savedR = dR; savedG = dG; savedB = dB; savedPeriod = dPeriod
        stopAnimation()
        takeoverActive = true
        root.setTakeover(true)   // keep the hook from letting the stock policy write
        spectrumSource = source
        projection = mediaProjection
        val analyzer = SpectrumAnalyzer(
            onFrame = { rr, gg, bb -> daemon.writeStateFast(1, 1, rr, gg, bb, 2000) },
            source = source,
            projection = mediaProjection,
            onLevels = { b, m, t -> bands = Triple(b, m, t) },
        )
        analyzer.sensitivity = sensitivity
        analyzer.maxLevel = maxLevel * brightnessCap
        if (source == SpectrumAnalyzer.Source.MIC) {
            appContext?.let { LightForegroundService.start(it, "音乐频谱同步中", true) }
        }
        if (!analyzer.start()) {
            takeoverActive = savedTakeover
            root.setTakeover(savedTakeover)
            releaseProjection()
            syncForeground()
            return false
        }
        spectrum = analyzer
        spectrumActive = true
        syncForeground()
        return true
    }

    fun stopSpectrum() {
        if (!spectrumActive) return
        spectrum?.stop()
        spectrum = null
        spectrumActive = false
        bands = Triple(0f, 0f, 0f)
        releaseProjection()
        takeoverActive = savedTakeover
        dMode = savedMode; dR = savedR; dG = savedG; dB = savedB; dPeriod = savedPeriod
        root.setTakeover(savedTakeover)
        if (takeoverActive) {
            pushDaemon()
        } else {
            daemon.writeState(0, dMode, dR, dG, dB, dPeriod)   // release the LED
            applyRgb(dR, dG, dB)
        }
        syncForeground()
    }

    fun setSpectrumSensitivity(s: Float) {
        spectrum?.sensitivity = s
    }

    /** Brightness ceiling as a fraction of full scale (0..1), e.g. 0.16 = 16%. */
    fun setSpectrumMaxLevel(f: Float) {
        spectrum?.maxLevel = f.coerceIn(0.01f, 1.0f)
    }

    /**
     * Full takeover via the root daemon. ON: install + start the daemon and make it enforce the
     * last chosen color/animation; the hook also drops the stock privacy write at its source so
     * there is no flash. OFF: release the LED back to the system.
     */
    fun setTakeover(enabled: Boolean): Boolean {
        return if (enabled) {
            if (!RootLightController.isRootAvailable()) {
                false
            } else {
                val ok = daemon.install() && daemon.start()
                if (ok) {
                    takeoverActive = true
                    stopAnimation()
                    pushDaemon()
                }
                root.setTakeover(true)   // flag consumed by the Xposed module
                ok
            }
        } else {
            takeoverActive = false
            daemon.writeState(0, dMode, dR, dG, dB, dPeriod)  // release the LED
            root.setTakeover(false)
            true
        }
    }

    /**
     * Force-stop the scoped app(s) so LSPosed re-injects the hook on their next start — the
     * equivalent of LSPosed's "restart scope". Requires root.
     */
    fun restartScope(): Boolean {
        if (!RootSession.isAvailable()) return false
        var ok = true
        for (pkg in HgConfig.SCOPE_PACKAGES) {
            if (!RootSession.exec("am force-stop $pkg", 5000).first) ok = false
        }
        return ok
    }

    /**
     * Re-send the current desired state so a changed brightness cap takes effect immediately.
     * The spectrum re-reads the cap on its own next frame, so it is left alone.
     */
    fun reapply() {
        if (spectrumActive) {
            spectrum?.maxLevel = spectrum?.maxLevel?.coerceAtMost(brightnessCap) ?: brightnessCap
            return
        }
        if (takeoverActive) pushDaemon() else if (dMode != 0) applyRgb(dR, dG, dB) else applyRgb(0, 0, 0)
    }

    // ---- custom trigger effects (a STATE, held until the condition ends) ----
    //
    // Layering, highest first: notification (momentary) > trigger (held) > manual/standing colour.
    // The music spectrum is an explicit continuous mode and is never interrupted by either.
    @Volatile
    var triggerActive = false
        private set

    private var gMode = 1
    private var gR = 0
    private var gG = 0
    private var gB = 0
    private var gPeriod = 2000

    /** Hold [color]/[effect] while a trigger condition is satisfied. */
    fun applyTriggerEffect(color: Int, effect: Int) {
        if (spectrumActive) return
        if (!triggerActive) {
            gMode = dMode; gR = dR; gG = dG; gB = dB; gPeriod = dPeriod
            triggerActive = true
        }
        when (effect) {
            NotifStore.EFFECT_BLINK -> startBlink(color, 800L)
            NotifStore.EFFECT_BREATH -> startBreathing(color, 2600L)
            else -> setColor(color)
        }
    }

    /** Condition ended: put back whatever was showing before the trigger took over. */
    fun clearTriggerEffect() {
        if (!triggerActive) return
        triggerActive = false
        val color = pack(gR, gG, gB)
        when (gMode) {
            0 -> off()
            2 -> startBreathing(color, gPeriod.toLong())
            3 -> startBlink(color, gPeriod.toLong())
            else -> setColor(color)
        }
    }

    // ---- transient notification effects (simple priority arbitration) ----
    //
    // Only ONE LED exists, so sources must be ordered. A notification is a short-lived EVENT: it
    // temporarily overrides the standing colour/animation and then restores it. The music spectrum
    // is an explicit user-driven mode that streams frames continuously, so notifications are
    // skipped while it runs rather than fighting it frame by frame.
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var transientActive = false
        private set

    private var tMode = 1
    private var tR = 0
    private var tG = 0
    private var tB = 0
    private var tPeriod = 2000

    private val restoreTransient = Runnable {
        transientActive = false
        val color = pack(tR, tG, tB)
        when (tMode) {
            0 -> off()
            2 -> startBreathing(color, tPeriod.toLong())
            3 -> startBlink(color, tPeriod.toLong())
            else -> setColor(color)
        }
    }

    /**
     * Show [color] for [durationMs] using [effect] (0 solid / 1 breathing / 2 blink), then restore
     * whatever was showing before. Repeated notifications simply extend the overlay.
     */
    fun showNotificationEffect(color: Int, effect: Int, durationMs: Long) {
        if (spectrumActive) return
        mainHandler.removeCallbacks(restoreTransient)
        if (!transientActive) {
            // remember the standing state exactly once, so a burst of notifications still restores
            // the original rather than the previous notification's colour
            tMode = dMode; tR = dR; tG = dG; tB = dB; tPeriod = dPeriod
            transientActive = true
        }
        when (effect) {
            NotifStore.EFFECT_SOLID -> setColor(color)
            NotifStore.EFFECT_BLINK -> startBlink(color, 600L)
            else -> startBreathing(color, 1600L)
        }
        if (durationMs > 0) {
            mainHandler.postDelayed(restoreTransient, durationMs.coerceIn(1000L, 120_000L))
        }
    }

    /** Manually end a persistent (duration=0) notification effect, e.g. when the notification is removed. */
    fun clearNotificationEffect() {
        if (!transientActive) return
        mainHandler.removeCallbacks(restoreTransient)
        restoreTransient.run()
    }

    /** Preview a rule without disturbing the standing state for longer than a moment. */
    fun previewNotificationEffect(color: Int, effect: Int) =
        showNotificationEffect(color, effect, 4000L)

    fun daemonRunning(): Boolean = daemon.isRunning()

    private fun pushDaemon() {
        daemon.writeState(
            if (takeoverActive) 1 else 0, dMode, cap(dR), cap(dG), cap(dB), dPeriod
        )
    }

    /**
     * Start/stop the foreground service to match the current foreground effect. A microphone-typed
     * FGS is required while the spectrum records in the background; a non-takeover animation just
     * needs the process kept alive. Takeover animations live in the daemon and need neither.
     */
    private fun releaseProjection() {
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
    }

    private fun syncForeground() {
        val ctx = appContext ?: return
        when {
            // The playback-capture spectrum already runs under the mediaProjection-typed service,
            // which must not be restarted with a different type while capturing.
            spectrumActive && spectrumSource == SpectrumAnalyzer.Source.PLAYBACK -> Unit
            spectrumActive -> LightForegroundService.start(ctx, "音乐频谱同步中", true)
            animator != null -> LightForegroundService.start(ctx, "灯效动画运行中", false)
            else -> LightForegroundService.stop(ctx)
        }
    }

    fun readTakeover(): Boolean = root.readTakeover()
    fun readHookLoaded(): Boolean = root.readHookLoaded()

    // ---- Privacy-light customization (written via root, consumed by the Xposed hook) ----

    fun setPrivacyMode(mode: Int): Boolean {
        val ok = root.putGlobal(K_PRIV_MODE, mode.toString())
        EventProvider.refreshPrivacyOverride()
        return ok
    }
    fun setPrivacyColor(color: Int): Boolean {
        val ok = root.putGlobal(K_PRIV_COLOR, color.toString())
        EventProvider.refreshPrivacyOverride()
        return ok
    }
    fun readPrivacyMode(): Int = root.getGlobal(K_PRIV_MODE, "0").toIntOrNull() ?: 0
    fun readPrivacyColor(): Int = root.getGlobal(K_PRIV_COLOR, "0").toIntOrNull() ?: 0

    // ---- Event monitoring (hook -> EventProvider binder IPC; Settings.Global is a fallback) ----

    fun readEventSeq(): Int {
        val cr = appContext?.contentResolver ?: return 0
        return try { Settings.Global.getInt(cr, K_EVT_SEQ, 0) } catch (e: Exception) { 0 }
    }

    fun readEventLast(): String {
        val cr = appContext?.contentResolver ?: return ""
        return try { Settings.Global.getString(cr, K_EVT_LAST) ?: "" } catch (e: Exception) { "" }
    }

    /** Buffered events from the hook (newest first), delivered via EventProvider. */
    fun bufferedEvents(): List<String> = EventProvider.snapshot()

    /**
     * Start receiving light events pushed by the hook (via EventProvider). The callback gets the
     * current snapshot immediately and again on every new event (on the main thread). Returns a
     * function that stops the stream.
     */
    fun observeEvents(onEvents: (List<String>) -> Unit): () -> Unit {
        val main = Handler(Looper.getMainLooper())
        onEvents(EventProvider.snapshot())
        EventProvider.setListener { list -> main.post { onEvents(list) } }
        return { EventProvider.setListener(null) }
    }

    /** "time|proc|method|0xcolor|pkg|styleType" -> a compact human-readable line. */
    fun formatEvent(raw: String): String {
        if (raw.isBlank()) return "(暂无事件)"
        val p = raw.split("|")
        if (p.size < 6) return raw
        val time = p[0].toLongOrNull()?.let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(it)) } ?: p[0]
        return "$time ${p[1]} ${p[2]} color=${p[3]} pkg=${p[4]} style=${p[5]}"
    }

    fun mode(): String = when {
        takeoverActive -> "ROOT/daemon"
        rootReady && root.ready -> "ROOT/sysfs"
        else -> "Binder/privacy"
    }

    fun getStatus(): String = "${binder.statusText} | mode: ${mode()}"

    fun diagnose(): String = binder.diagnose() +
            "mode: ${mode()}\n" +
            "rootReady: ${rootReady}\n" +
            "takeoverActive: ${takeoverActive}\n" +
            "spectrumActive: ${spectrumActive}\n" +
            "daemonRunning: ${daemonRunning()}\n" +
            "daemonState: ${daemon.readState()}\n" +
            "privacyMode: ${readPrivacyMode()}\n" +
            "privacyColor: 0x${Integer.toHexString(readPrivacyColor())}\n" +
            "takeoverFlag: ${readTakeover()}\n" +
            "hookLoaded: ${readHookLoaded()}\n" +
            "events(buffered): ${EventProvider.snapshot().size}\n" +
            "evtSeq: ${readEventSeq()}\n" +
            "evtLast: ${readEventLast()}\n"

    fun setPkg(p: String) { binder.pkg = p }

    private fun pack(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun unpack(color: Int): Triple<Int, Int, Int> =
        Triple((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
}
