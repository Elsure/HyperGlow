package com.elsure.hyperglow

import android.util.Log

/**
 * Direct RGB LED control via sysfs plus the Settings.Global switches shared with the in-app Xposed
 * module (LightHook):
 *  - miuilight_takeover     : kill switch for the stock light policy (app writes, hook reads).
 *  - miuilight_hook_loaded  : marker the system_server hook writes on load (app reads to confirm).
 *
 * Nodes: /sys/class/leds/{red,green,blue}/brightness, range 0..255. All root work now goes through
 * the shared [RootSession] (one su shell for hot sysfs writes, one-shot exec for settings), so this
 * class no longer owns a private su shell.
 */
class RootLightController {

    companion object {
        private const val TAG = "MiuiLight"
        private const val RED = HgConfig.LED_RED
        private const val GREEN = HgConfig.LED_GREEN
        private const val BLUE = HgConfig.LED_BLUE

        const val TAKEOVER_KEY = "miuilight_takeover"
        const val MARKER_KEY = "miuilight_hook_loaded"

        fun isRootAvailable(): Boolean = RootSession.isAvailable(force = true)
    }

    @Volatile
    var ready = false
        private set

    /** Warm up the shared root shell; [ready] reflects whether sysfs writes can go through. */
    @Synchronized
    fun acquire(): Boolean {
        if (ready) return true
        ready = RootSession.isAvailable() && RootSession.write("id")
        if (ready) Log.i(TAG, "root acquired (shared session)")
        return ready
    }

    @Synchronized
    fun setRgb(r: Int, g: Int, b: Int) {
        if (!ready) return
        val cmd = "echo ${r.coerceIn(0, 255)} > $RED; " +
                "echo ${g.coerceIn(0, 255)} > $GREEN; " +
                "echo ${b.coerceIn(0, 255)} > $BLUE"
        if (!RootSession.write(cmd)) ready = false
    }

    fun off() = setRgb(0, 0, 0)

    fun setTakeover(enabled: Boolean): Boolean {
        val ok = RootSession.exec("settings put global $TAKEOVER_KEY ${if (enabled) "1" else "0"}").first
        Log.i(TAG, "setTakeover=${if (enabled) 1 else 0} ok=$ok")
        return ok
    }

    fun readTakeover(): Boolean = readGlobalFlag(TAKEOVER_KEY)

    /** True if the in-app Xposed module's system_server hook has loaded (it writes this marker). */
    fun readHookLoaded(): Boolean = readGlobalFlag(MARKER_KEY)

    private fun readGlobalFlag(key: String): Boolean =
        RootSession.exec("settings get global $key").second == "1"

    /** Write a Settings.Global value via root (the app lacks WRITE_SECURE_SETTINGS). */
    fun putGlobal(key: String, value: String): Boolean =
        RootSession.exec("settings put global $key $value").first

    /** Read a Settings.Global value via root; returns [default] when unset. */
    fun getGlobal(key: String, default: String): String {
        val v = RootSession.exec("settings get global $key").second
        return if (v.isEmpty() || v == "null") default else v
    }

    /**
     * Drop the sysfs-direct flag. The shared [RootSession] stays alive so the daemon/privacy fast
     * writes keep working; it is torn down only on app exit via RootSession.close().
     */
    @Synchronized
    fun release() {
        ready = false
    }
}
