package com.elsure.hyperglow

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Evaluates the user's custom trigger rules and holds the LED for whichever condition is active.
 *
 * Runs as a foreground service because several of the signals (screen on/off, battery level,
 * bluetooth ACL) can only be received by a dynamically registered receiver, which requires a live
 * process. It starts only while at least one rule is enabled.
 */
class TriggerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var charging = false
    private var batteryPct = 100
    private var btConnected = false
    private var headset = false
    private var screenOff = false
    private var foregroundPkg = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
                Intent.ACTION_POWER_CONNECTED -> charging = true
                Intent.ACTION_POWER_DISCONNECTED -> charging = false
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_SCREEN_ON -> screenOff = false
                BluetoothDevice.ACTION_ACL_CONNECTED -> btConnected = true
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> btConnected = false
                AudioManager.ACTION_HEADSET_PLUG ->
                    headset = intent.getIntExtra("state", 0) == 1
            }
            evaluate()
        }
    }

    /** Polls the foreground app; only scheduled when an app rule exists and access is granted. */
    private val pollForeground = object : Runnable {
        override fun run() {
            foregroundPkg = currentForegroundApp()
            evaluate()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGlow")
            .setContentText("自定义触发监听中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            try { startForeground(NOTIF_ID, notif) } catch (_: Exception) {}
        }

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (TriggerStore.rules(this).any { it.enabled && it.type == TriggerStore.T_APP_FOREGROUND }) {
            handler.post(pollForeground)
        }
        evaluate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        heldId = -1   // rules may have changed; re-decide from scratch
        evaluate()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        LightController.clearTriggerEffect()
    }

    /** id of the rule currently driving the LED, so we only act on real transitions */
    private var heldId = -1

    /**
     * Pick the highest-priority satisfied rule and drive the LED; release when none match.
     *
     * Only transitions act: ACTION_BATTERY_CHANGED alone fires constantly, and re-applying every
     * time would restart the animation several times a second.
     */
    private fun evaluate() {
        val active = TriggerStore.rules(this)
            .filter { it.enabled && matches(it) }
            .maxByOrNull { it.priority }
        val id = active?.id ?: -1
        if (id == heldId) return
        heldId = id
        when {
            active == null -> LightController.clearTriggerEffect()
            active.durationSec > 0 -> {
                // timed: flash, then fall back to whatever was showing
                LightController.clearTriggerEffect()
                LightController.showNotificationEffect(
                    active.color, active.effect, active.durationSec * 1000L
                )
            }
            else -> LightController.applyTriggerEffect(active.color, active.effect)
        }
    }

    private fun matches(r: TriggerStore.Rule): Boolean = when (r.type) {
        TriggerStore.T_BATTERY_LOW -> batteryPct <= (r.param.toIntOrNull() ?: 20)
        TriggerStore.T_CHARGING -> charging
        TriggerStore.T_BATTERY_FULL -> batteryPct >= 100
        TriggerStore.T_BLUETOOTH -> btConnected
        TriggerStore.T_HEADSET -> headset
        TriggerStore.T_SCREEN_OFF -> screenOff
        TriggerStore.T_APP_FOREGROUND -> r.param.isNotBlank() && foregroundPkg == r.param
        else -> false
    }

    private fun currentForegroundApp(): String = try {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 15_000, now)
            ?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
    } catch (e: Exception) {
        Log.w(TAG, "usage stats unavailable: ${e.message}")
        ""
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "自定义触发", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    companion object {
        private const val TAG = "MiuiLight"
        private const val CHANNEL = "hyperglow_trigger"
        private const val NOTIF_ID = 1002

        /** Start when any rule is enabled, stop otherwise. Safe to call repeatedly. */
        fun sync(ctx: Context) {
            val i = Intent(ctx, TriggerService::class.java)
            if (TriggerStore.anyEnabled(ctx)) ContextCompat.startForegroundService(ctx, i)
            else ctx.stopService(i)
        }

        /** Whether the user has granted 使用情况访问权限 (needed for the foreground-app rule). */
        fun hasUsageAccess(ctx: Context): Boolean = try {
            val ops = ctx.getSystemService(AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}
