package com.elsure.hyperglow

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Controls the physical RGB status LED on HyperOS devices (e.g. Xiaomi Pad 8 Pro / "piano")
 * through the MIUI privacy-light binder path. Verified on-device.
 *
 * Acquisition:
 *   ServiceManager.getService("notification")
 *     -> transact(163)   [= INotificationManager.getColorLightManager; raw transact bypasses
 *                           the hidden-API reflection block that makes getMethod() throw]
 *     -> miui.lights.ILightsManager binder
 * Control:
 *     -> transact(2)     [= setColorCommon(color, pkg, styleType, userId)]
 *        with styleType = 7  -> MiuiLightsService.AnonymousClass2.setColorCommon
 *        -> mPrivacyLight.setColorCommonLocked(color, 0, 0, 0, 0)
 *
 * styleType 7 is the camera-privacy indicator channel. It has NO permission check and NO
 * led-strip enable gate, so a normal (non-root, non-system) app can drive the RGB LED with
 * any color. color == 0 turns the LED off. The state is cached inside the system service,
 * so a steady color persists after the app is killed until it is changed or cleared.
 *
 * The [pkg] argument is NOT validated by the privacy path, so it can be changed at runtime
 * to experiment with decoupling our light from the real camera lifecycle (which shares this
 * channel and otherwise overrides/clears the LED when the camera opens/closes).
 */
class MiuiLightController {

    companion object {
        private const val TAG = "MiuiLight"
        private const val NOTIF_DESCRIPTOR = HgConfig.NOTIF_DESCRIPTOR
        private const val TRANSACTION_GET_COLOR_LIGHT_MANAGER = HgConfig.TX_GET_COLOR_LIGHT_MANAGER
        private const val LIGHTS_DESCRIPTOR = HgConfig.LIGHTS_DESCRIPTOR
        private const val TX_SET_COLOR_COMMON = HgConfig.TX_SET_COLOR_COMMON
        private const val STYLE_PRIVACY = HgConfig.STYLE_PRIVACY
        const val DEFAULT_PKG = HgConfig.DEFAULT_PKG
    }

    private var lightsBinder: IBinder? = null

    /** Package name sent with setColorCommon. Changeable for the camera-decoupling experiment. */
    var pkg: String = DEFAULT_PKG

    var statusText = "not initialized"
        private set

    fun init() {
        if (lightsBinder != null) return
        try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getDeclaredMethod("getService", String::class.java)
            val notifBinder = getService.invoke(null, "notification") as? IBinder
            if (notifBinder == null) { statusText = "FAIL: notification binder null"; return }

            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(NOTIF_DESCRIPTOR)
                val ok = notifBinder.transact(TRANSACTION_GET_COLOR_LIGHT_MANAGER, data, reply, 0)
                if (!ok) { statusText = "FAIL: transact 163 false"; return }
                reply.readException()
                lightsBinder = reply.readStrongBinder()
                statusText = if (lightsBinder != null)
                    "OK (${lightsBinder!!.interfaceDescriptor})"
                else "FAIL: getColorLightManager null"
            } finally { data.recycle(); reply.recycle() }
        } catch (e: Exception) {
            statusText = "FAIL: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "init failed", e)
        }
    }

    /** Set the LED to a steady ARGB color. color == 0 turns it off. */
    fun setColor(color: Int) {
        if (lightsBinder == null) init()
        val binder = lightsBinder ?: return
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(LIGHTS_DESCRIPTOR)
            data.writeInt(color)         // color (ARGB)
            data.writeString(pkg)        // pkg (not validated by the privacy path)
            data.writeInt(STYLE_PRIVACY) // styleType = 7 (privacy light)
            data.writeInt(0)             // userId
            binder.transact(TX_SET_COLOR_COMMON, data, reply, 0)
            try { reply.readException() } catch (e: Exception) { Log.e(TAG, "setColor EX", e) }
            Log.i(TAG, "setColor 0x${Integer.toHexString(color)} pkg=$pkg")
        } catch (e: Exception) {
            Log.e(TAG, "setColor failed", e)
        } finally { data.recycle(); reply.recycle() }
    }

    fun off() = setColor(0)

    fun diagnose(): String {
        val binder = lightsBinder
        return buildString {
            appendLine("binder: $binder")
            if (binder != null) {
                appendLine("descriptor: ${runCatching { binder.interfaceDescriptor }.getOrDefault("?")}")
                appendLine("alive: ${binder.isBinderAlive}")
            }
            appendLine("pkg: $pkg")
            appendLine("status: $statusText")
        }
    }
}