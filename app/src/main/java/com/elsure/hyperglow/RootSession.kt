package com.elsure.hyperglow

import android.util.Log
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * The single owner of root (`su`) access for the whole app. Replaces the two previously separate
 * long-lived shells (RootShell + RootLightController's own) and the scattered one-shot `su -c`
 * spawns, so there is exactly one place that manages root.
 *
 * Two channels:
 *  - [write] : fire-and-forget on ONE shared long-lived interactive shell (~1ms pipe write),
 *              used by the hot paths (sysfs LED writes, privacy-override file, spectrum frames)
 *              so they never pay the per-`su` spawn cost.
 *  - [exec]  : a one-shot `su -c` when the caller needs the exit code and/or stdout (settings
 *              get/put, daemon install/start/stop/status).
 *
 * All shared-shell access is synchronized so the binder thread (EventProvider), the analyzer
 * thread (SpectrumAnalyzer) and the UI/root threads are safe. stdout/stderr are drained so the
 * shell never blocks on a full pipe.
 */
object RootSession {

    private const val TAG = "MiuiLight"

    private var proc: Process? = null
    private var stdin: OutputStream? = null
    private val lock = Any()

    @Volatile
    private var available: Boolean? = null

    /** Cached root check; pass force=true to re-probe (e.g. right after the user grants root). */
    fun isAvailable(force: Boolean = false): Boolean {
        val cached = available
        if (cached != null && !force) return cached
        val ok = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val done = p.waitFor(2500, TimeUnit.MILLISECONDS)
            if (!done) { p.destroy(); false }
            else p.inputStream.bufferedReader().readText().contains("uid=0")
        } catch (e: Exception) {
            Log.w(TAG, "root check failed: ${e.message}"); false
        }
        available = ok
        return ok
    }

    /** Fire-and-forget command on the shared long-lived shell. false if root is unavailable. */
    fun write(cmd: String): Boolean {
        synchronized(lock) {
            if (!ensureLocked()) return false
            return try {
                val os = stdin!!
                os.write((cmd + "\n").toByteArray()); os.flush(); true
            } catch (e: Exception) {
                Log.w(TAG, "RootSession write failed, resetting: ${e.message}")
                proc = null; stdin = null; false
            }
        }
    }

    /** One-shot command; returns (exit code == 0, trimmed stdout). */
    fun exec(cmd: String, timeoutMs: Long = 3000): Pair<Boolean, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) { p.destroy(); return false to "" }
            val out = p.inputStream.bufferedReader().readText().trim()
            (p.exitValue() == 0) to out
        } catch (e: Exception) {
            Log.w(TAG, "RootSession exec failed: $cmd (${e.message})")
            false to ""
        }
    }

    /**
     * One-shot command whose output may be large. [exec] waits for the process BEFORE reading
     * stdout, which deadlocks once the output exceeds the pipe buffer (~64 KB); this drains stdout
     * on a separate thread instead. Used for dumping /data/system/notification_policy.xml.
     */
    fun execLarge(cmd: String, timeoutMs: Long = 10_000): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val sb = StringBuilder()
            val drain = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { sb.append(it).append('\n') }
                } catch (_: Exception) {
                }
            }
            drain.start()
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) p.destroy()
            drain.join(1500)
            sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "execLarge failed: ${e.message}")
            ""
        }
    }

    private fun ensureLocked(): Boolean {
        val p = proc
        if (p != null && p.isAlive) return true
        return try {
            val np = Runtime.getRuntime().exec("su")
            Thread { try { np.inputStream.bufferedReader().forEachLine { } } catch (_: Exception) {} }.start()
            Thread { try { np.errorStream.bufferedReader().forEachLine { } } catch (_: Exception) {} }.start()
            val os = np.outputStream
            os.write("id\n".toByteArray()); os.flush()
            proc = np; stdin = os
            available = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "RootSession acquire failed: ${e.message}")
            proc = null; stdin = null; false
        }
    }

    fun close() {
        synchronized(lock) {
            try { stdin?.write("exit\n".toByteArray()); stdin?.flush() } catch (_: Exception) {}
            proc?.destroy(); proc = null; stdin = null
        }
    }
}
