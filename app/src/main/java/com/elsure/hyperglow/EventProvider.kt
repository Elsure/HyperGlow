package com.elsure.hyperglow

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import java.io.File

/**
 * Cross-process sink for light events. The Xposed hook (running in com.miui.securitycenter and
 * other scoped processes) delivers each intercepted light call here via
 *   contentResolver.call(content://com.elsure.hyperglow.events, "event", "<evt>", null)
 *
 * A ContentProvider call is plain binder IPC that any process can make to an exported provider, so
 * this works even though the target process lacks WRITE_SECURE_SETTINGS.
 *
 * Two jobs:
 *  1. Keep a small ring file of events (filesDir) for the UI to display.
 *  2. For privacy events (setColorCommon, styleType==7) translate the user's privacy preference
 *     into the daemon's privacy-override file (/data/adb/miuilight/privacy, written via root), so
 *     that during full takeover the daemon shows the custom privacy color while the camera is
 *     active and reverts to the base state when it closes.
 */
class EventProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.elsure.hyperglow.events"
        private const val MAX = 30
        private const val PRIV_DIR = "/data/adb/miuilight"
        private const val PRIV_FILE = "$PRIV_DIR/privacy"
        private const val K_PRIV_MODE = "miuilight_privacy_mode"
        private const val K_PRIV_COLOR = "miuilight_privacy_color"

        private var file: File? = null
        private var appCtx: Context? = null
        private var listener: ((List<String>) -> Unit)? = null

        // Whether a privacy source (camera) is currently active, from the last privacy event.
        @Volatile
        private var privacyActive = false

        fun setListener(l: ((List<String>) -> Unit)?) {
            listener = l
        }

        /** Buffered events, newest first. */
        fun snapshot(): List<String> {
            val f = file ?: return emptyList()
            return try {
                if (f.exists()) f.readLines().filter { it.isNotBlank() }.asReversed() else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** Recompute and write the daemon privacy override from current state + settings. */
        fun refreshPrivacyOverride() = writePrivacyOverride()

        private fun push(event: String) {
            val f = file ?: return
            try {
                val lines = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
                lines.add(event)
                while (lines.size > MAX) lines.removeAt(0)
                f.writeText(lines.joinToString("\n"))
            } catch (e: Exception) {
                // persistence is best-effort; still handle the event below
            }
            try {
                listener?.invoke(snapshot())
            } catch (e: Exception) {
                // ignore listener errors
            }
            handlePrivacyEvent(event)
        }

        /** If this event is a privacy-light change, update the daemon override file. */
        private fun handlePrivacyEvent(event: String) {
            val p = event.split("|")
            if (p.size < 6) return
            val method = p[2]
            val style = p[5].toIntOrNull() ?: return
            if (method != "setColorCommon" || style != 7) return
            val color = try {
                p[3].removePrefix("0x").toLong(16).toInt()
            } catch (e: Exception) {
                return
            }
            privacyActive = (color != 0)
            writePrivacyOverride()
        }

        private fun writePrivacyOverride() {
            val ctx = appCtx ?: return
            val cr = ctx.contentResolver
            val mode = try { Settings.Global.getInt(cr, K_PRIV_MODE, 0) } catch (e: Exception) { 0 }
            val override = when {
                !privacyActive -> "0,0,0,0"
                mode == 1 -> {
                    val pc = try { Settings.Global.getInt(cr, K_PRIV_COLOR, 0) } catch (e: Exception) { 0 }
                    "1,${(pc shr 16) and 0xFF},${(pc shr 8) and 0xFF},${pc and 0xFF}"
                }
                mode == 2 -> "2,0,0,0"
                else -> "0,0,0,0"
            }
            val cmd = "mkdir -p $PRIV_DIR && echo '$override' > $PRIV_FILE.tmp && mv $PRIV_FILE.tmp $PRIV_FILE"
            // Shared long-lived root shell (RootSession): a ~1ms pipe write instead of spawning a su process,
            // so the custom privacy color lands on the daemon almost instantly.
            RootSession.write(cmd)
        }
    }

    override fun onCreate(): Boolean {
        val ctx = context
        if (ctx != null) {
            file = File(ctx.filesDir, "miuilight_events.txt")
            appCtx = ctx.applicationContext
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isCallerTrusted()) return null
        if (method == "event" && !arg.isNullOrBlank()) push(arg)
        return null
    }

    /**
     * Only accept calls from trusted callers: this app, the system, or securitycenter (where the
     * light hook runs). This provider is exported so the cross-process hook can reach it, which
     * would otherwise let ANY app inject fake events or trigger the root-backed privacy override.
     */
    private fun isCallerTrusted(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == Process.SYSTEM_UID) return true
        val pkgs = context?.packageManager?.getPackagesForUid(uid) ?: return false
        return pkgs.any { it == "com.miui.securitycenter" || it.startsWith("com.elsure.hyperglow") }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0
}