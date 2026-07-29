package com.elsure.hyperglow

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Installs and drives the root daemon (miuilightd). All root work is delegated to the shared
 * [RootSession]: one-shot [RootSession.exec] for install/start/stop/status and the atomic state
 * write, and the shared long-lived shell ([RootSession.write]) for the low-latency spectrum path.
 */
class DaemonController {

    companion object {
        private const val TAG = "MiuiLight"
        private const val DIR = HgConfig.DAEMON_DIR
        private const val DAEMON = "$DIR/miuilightd"
        private const val PIDFILE = "$DIR/miuilightd.pid"
        private const val STATE = "$DIR/state"
        private const val ASSET = "miuilightd"
    }

    private var appContext: Context? = null

    @Volatile
    var installed = false
        private set

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
    }

    /** Run a root command; true only if it finishes in time with exit code 0. */
    private fun su(cmd: String, timeoutMs: Long = 4000): Boolean =
        RootSession.exec(cmd, timeoutMs).first

    private fun suOut(cmd: String): String =
        RootSession.exec(cmd, 3000).second

    /** Copy the bundled daemon script to /data/adb/miuilight and make it executable. */
    fun install(): Boolean {
        val ctx = appContext ?: return false
        return try {
            val script = ctx.assets.open(ASSET).bufferedReader().readText()
            val stage = File(ctx.cacheDir, "miuilightd")
            stage.writeText(script)
            // chown is best-effort (some roots disallow it); cp+chmod must succeed.
            val cmd = "mkdir -p $DIR && cp ${stage.absolutePath} $DAEMON && chmod 755 $DAEMON && " +
                    "{ chown root:root $DAEMON 2>/dev/null || true; }"
            val ran = su(cmd)
            val present = suOut("[ -f $DAEMON ] && echo yes").contains("yes")
            installed = ran && present
            Log.i(TAG, "daemon install ran=$ran present=$present")
            installed
        } catch (e: Exception) {
            Log.e(TAG, "daemon install failed", e)
            false
        }
    }

    /** Kill any instance recorded in the pidfile, launch a fresh detached daemon, verify it runs. */
    fun start(): Boolean {
        val cmd = "if [ -f $PIDFILE ]; then kill \$(cat $PIDFILE) 2>/dev/null; fi; " +
                "rm -f $PIDFILE; " +
                "setsid /system/bin/sh $DAEMON >/dev/null 2>&1 &"
        val launched = su(cmd)
        if (!launched) return false
        // give the daemon a moment to write its pidfile, then confirm it is alive
        var alive = false
        var tries = 0
        while (tries < 6 && !alive) {
            try { Thread.sleep(120) } catch (_: InterruptedException) {}
            alive = isRunning()
            tries++
        }
        Log.i(TAG, "daemon start launched=$launched alive=$alive")
        return alive
    }

    fun stop(): Boolean =
        su("if [ -f $PIDFILE ]; then kill \$(cat $PIDFILE) 2>/dev/null; fi; rm -f $PIDFILE")

    fun isRunning(): Boolean {
        val out = suOut("[ -f $PIDFILE ] && [ -d /proc/\$(cat $PIDFILE) ] && echo running")
        return out.contains("running")
    }

    /** Atomic write of: takeover,mode,r,g,b,period. */
    fun writeState(takeover: Int, mode: Int, r: Int, g: Int, b: Int, period: Int): Boolean {
        val line = "$takeover,$mode," +
                "${r.coerceIn(0, 255)},${g.coerceIn(0, 255)},${b.coerceIn(0, 255)}," +
                "${period.coerceAtLeast(100)}"
        val cmd = "mkdir -p $DIR && echo '$line' > $STATE.tmp && mv $STATE.tmp $STATE"
        return su(cmd, 2500)
    }

    /**
     * Low-latency state write through the shared long-lived root shell (no per-call su spawn).
     * Used by the music spectrum, which pushes ~21 frames/sec. The write is non-atomic, but the
     * daemon only accepts a line whose period field parses as a number, so a torn read is simply
     * ignored (the daemon keeps its last good state) rather than corrupting the LED.
     */
    fun writeStateFast(takeover: Int, mode: Int, r: Int, g: Int, b: Int, period: Int): Boolean {
        val line = "$takeover,$mode," +
                "${r.coerceIn(0, 255)},${g.coerceIn(0, 255)},${b.coerceIn(0, 255)}," +
                "${period.coerceAtLeast(100)}"
        return RootSession.write("echo '$line' > $STATE")
    }

    fun readState(): String = suOut("cat $STATE 2>/dev/null")
}
