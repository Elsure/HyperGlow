package com.elsure.hyperglow

/**
 * Central home for the values most likely to break across devices / HyperOS versions, so an OTA
 * only needs edits here. The Xposed hook (LightHook.java) keeps its own copies of the Settings
 * keys because it is a separate Java compile unit loaded into other processes; those key STRINGS
 * must stay identical on both sides.
 */
object HgConfig {

    // sysfs RGB LED nodes — verified on Xiaomi Pad 8 Pro ("piano"); range 0..255.
    const val LED_RED = "/sys/class/leds/red/brightness"
    const val LED_GREEN = "/sys/class/leds/green/brightness"
    const val LED_BLUE = "/sys/class/leds/blue/brightness"

    // Flashlight / torch LED (PM8550): switch enables power rail, torch_0 sets intensity 0..150.
    const val LED_SWITCH = "/sys/class/leds/led:switch_2/brightness"
    const val LED_TORCH = "/sys/class/leds/led:torch_0/brightness"

    // MIUI privacy-light binder path — transact codes & descriptors are version-fragile.
    const val NOTIF_DESCRIPTOR = "android.app.INotificationManager"
    const val TX_GET_COLOR_LIGHT_MANAGER = 163      // INotificationManager.getColorLightManager
    const val LIGHTS_DESCRIPTOR = "miui.lights.ILightsManager"
    const val TX_SET_COLOR_COMMON = 2               // ILightsManager.setColorCommon
    const val STYLE_PRIVACY = 7                      // camera privacy-indicator channel
    const val DEFAULT_PKG = "com.android.camera"

    // LSPosed scope packages that can be force-stopped to reload the hook. "android"
    // (system_server) is deliberately NOT here — restarting it is a reboot.
    val SCOPE_PACKAGES = listOf("com.miui.securitycenter")

    // Root daemon runtime directory (state/pid/binary derived from this).
    const val DAEMON_DIR = "/data/adb/miuilight"
}
