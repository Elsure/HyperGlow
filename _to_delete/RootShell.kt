package com.elsure.hyperglow

import android.util.Log
import java.io.OutputStream

/**
 * A single long-lived interactive root shell shared by low-latency writers (privacy-override file,
 * music-spectrum frames). Writing a command is just a pipe write (~1ms) instead of spawning a new
 * `su` process each time (~tens of ms). Commands are fire-and-forget; stdout/stderr are drained so
 * the shell never blocks on a full pipe. All access is synchronized so concurrent callers (binder
 * thread from EventProvider, analyzer thread from SpectrumAnalyzer) are safe.
 */
object RootShell {

    private const val TAG = "MiuiLight"

    private var proc: Process? = null
    private var stdin: OutputStream? = null
    private val lock = Any()

    /** Write one shell command line. Returns false if root is unavailable. */
    fun write(cmd: String): Boolean {
        synchronized(lock) {
            if (!ensureLocked()) return false
            return try {
                val os = stdin!!
                os.write((cmd + "\n").toByteArray())
                os.flush()
                true
            } catch (e: Exception) {
                Log.w(TAG, "RootShell write failed, resetting: ${e.message}")
                proc = null
                stdin = null
                false
            }
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
            os.write("id\n".toByteArray())
            os.flush()
            proc = np
            stdin = os
            true
        } catch (e: Exception) {
            Log.w(TAG, "RootShell acquire failed: ${e.message}")
            proc = null
            stdin = null
            false
        }
    }

    fun close() {
        synchronized(lock) {
            try { stdin?.write("exit\n".toByteArray()); stdin?.flush() } catch (_: Exception) {}
            proc?.destroy()
            proc = null
            stdin = null
        }
    }
}