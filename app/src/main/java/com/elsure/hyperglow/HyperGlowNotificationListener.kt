package com.elsure.hyperglow

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
    private var connectedAt = 0L
    private val recentKeys = mutableMapOf<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        if (n.postTime < connectedAt - 1000) return
        if ((n.notification?.flags ?: 0) and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0) return

        val now = System.currentTimeMillis()
        val last = recentKeys[n.key] ?: 0L
        if (now - last < 3000) return
        recentKeys[n.key] = now
        if (recentKeys.size > 64) recentKeys.clear()

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
        if (persistentKeys.remove(n.key) && persistentKeys.isEmpty()) {
            Log.i(TAG, "persistent notif removed -> restore")
            LightController.clearNotificationEffect()
        }
    }

    override fun onListenerConnected() {
        connectedAt = System.currentTimeMillis()
        Log.i(TAG, "notification listener connected")
    }

    companion object {
        private const val TAG = "MiuiLight"
    }
}
