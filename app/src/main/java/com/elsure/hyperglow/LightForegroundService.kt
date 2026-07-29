package com.elsure.hyperglow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps the process foregrounded while a foreground light effect runs, so it survives the screen
 * turning off and the app going to background:
 *  - music spectrum (mic)      : microphone-typed FGS, required to keep recording in background;
 *  - music spectrum (playback) : mediaProjection-typed FGS. Android 14+ requires the service to be
 *                                in the foreground with this type BEFORE getMediaProjection() is
 *                                called, so the projection is created HERE rather than in the UI;
 *  - non-takeover animation    : dataSync-typed, just to keep the process alive.
 * Takeover animations run inside the root daemon and never need this service.
 */
class LightForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        ensureChannel()

        if (action == ACTION_START_PROJECTION) {
            startInForeground("音乐频谱同步中（内录）", ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            startProjectionSpectrum(intent)
            return START_NOT_STICKY   // a projection token cannot be recreated after a restart
        }

        val mic = intent?.getBooleanExtra(EXTRA_MIC, false) ?: false
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "灯效运行中"
        startInForeground(
            text,
            if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        return START_STICKY
    }

    private fun startInForeground(text: String, type: Int) {
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGlow")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIF_ID, notif, type)
        } catch (e: Exception) {
            // e.g. a typed FGS refused because its permission isn't granted; degrade gracefully.
            Log.w(TAG, "startForeground(type=$type) failed: ${e.message}")
            try { startForeground(NOTIF_ID, notif) } catch (_: Exception) {}
        }
    }

    /** Create the MediaProjection (now that we are a foreground mediaProjection service) and run. */
    private fun startProjectionSpectrum(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        val sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, 1.0f)
        val maxLevel = intent.getFloatExtra(EXTRA_MAX_LEVEL, 0.16f)
        if (data == null) { stopSelf(); return }

        val projection: MediaProjection? = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e); null
        }
        if (projection == null) { stopSelf(); return }

        // Android 14+ requires a callback registered before the projection is used.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "projection stopped by system/user")
                LightController.stopSpectrum()
            }
        }, Handler(Looper.getMainLooper()))

        Thread {
            val ok = LightController.startSpectrum(
                sensitivity = sensitivity,
                maxLevel = maxLevel,
                source = SpectrumAnalyzer.Source.PLAYBACK,
                mediaProjection = projection,
            )
            Log.i(TAG, "playback spectrum started=$ok")
            if (!ok) {
                try { projection.stop() } catch (_: Exception) {}
                stopSelf()
            }
        }.start()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "灯效运行", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val TAG = "MiuiLight"
        private const val CHANNEL = "hyperglow_fx"
        private const val NOTIF_ID = 1001
        private const val EXTRA_MIC = "mic"
        private const val EXTRA_TEXT = "text"

        const val ACTION_START_PROJECTION = "com.elsure.hyperglow.START_PROJECTION"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"
        private const val EXTRA_SENSITIVITY = "sensitivity"
        private const val EXTRA_MAX_LEVEL = "maxLevel"

        /** Start (or refresh) the foreground service. [mic] selects the microphone service type. */
        fun start(ctx: Context, text: String, mic: Boolean) {
            val i = Intent(ctx, LightForegroundService::class.java)
                .putExtra(EXTRA_MIC, mic)
                .putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(ctx, i)
        }

        /**
         * Hand the screen-capture consent result to the service, which becomes a foreground
         * mediaProjection service and only then creates the projection + starts the spectrum.
         */
        fun startPlaybackSpectrum(
            ctx: Context,
            resultCode: Int,
            resultData: Intent,
            sensitivity: Float,
            maxLevel: Float,
        ) {
            val i = Intent(ctx, LightForegroundService::class.java)
                .setAction(ACTION_START_PROJECTION)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
                .putExtra(EXTRA_SENSITIVITY, sensitivity)
                .putExtra(EXTRA_MAX_LEVEL, maxLevel)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LightForegroundService::class.java))
        }
    }
}
