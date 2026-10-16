package com.elsure.hyperglow

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Watches posted notifications and drives a transient light effect for the ones the user has
 * configured. Bound by the system once the user grants notification access in Settings.
 *
 * Ongoing/foreground-service notifications and this app's own are ignored: they are status
 * indicators rather than events, and would otherwise re-trigger constantly.
 */
class HyperGlowNotificationListener : NotificationListenerService() {

    /** Keys of notifications currently holding a persistent (duration=0) light effect. */
    private val persistentKeys = mutableSetOf<String>()
    private val recentKeys = mutableMapOf<String, Long>()
    private val knownSignatures = mutableMapOf<String, String>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        if (n.isOngoing) return
        val flags = n.notification?.flags ?: 0
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.isGroup && flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val sig = signatureOf(n)
        val prevSig = knownSignatures[n.key]
        if (prevSig != null && prevSig == sig) {
            // Notification reposted without any actual content change -> ignore
            return
        }
        knownSignatures[n.key] = sig
        if (knownSignatures.size > 256) {
            val toDrop = knownSignatures.keys.take(128).toSet()
            toDrop.forEach { knownSignatures.remove(it) }
        }

        val now = System.currentTimeMillis()
        val last = recentKeys[n.key] ?: 0L
        if (now - last < 1500) return
        recentKeys[n.key] = now
        if (recentKeys.size > 128) recentKeys.clear()

        val channel = n.notification?.channelId ?: NotifStore.ANY_CHANNEL
        NotifStore.recordSeen(this, n.packageName, channel)

        val rule = NotifStore.match(this, n.packageName, channel) ?: return
        if (!rule.enabled) return
        Log.i(TAG, "notif ${n.packageName}/$channel -> effect=${rule.effect} dur=${rule.durationSec}")
        LightController.showNotificationEffect(rule.color, rule.effect, rule.durationSec * 1000L)
        if (rule.durationSec <= 0) {
            persistentKeys.add(n.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        knownSignatures.remove(n.key)
        recentKeys.remove(n.key)
        if (persistentKeys.remove(n.key) && persistentKeys.isEmpty()) {
            Log.i(TAG, "persistent notif removed -> restore")
            LightController.clearNotificationEffect()
        }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "notification listener connected")
        LightController.setNotificationListenerActive(true)
        try {
            activeNotifications?.forEach { sbn ->
                knownSignatures[sbn.key] = signatureOf(sbn)
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to snapshot active notifications on connect", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "notification listener disconnected")
        LightController.setNotificationListenerActive(false)
        knownSignatures.clear()
        recentKeys.clear()
        persistentKeys.clear()
    }

    private fun signatureOf(n: StatusBarNotification): String {
        val notif = n.notification ?: return ""
        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notif.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = notif.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val whenTime = notif.`when`
        val number = notif.number
        return "$title|$text|$subText|$whenTime|$number"
    }

    companion object {
        private const val TAG = "MiuiLight"
    }
}
