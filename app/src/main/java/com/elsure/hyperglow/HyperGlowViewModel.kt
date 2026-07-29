package com.elsure.hyperglow

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A launchable installed app, for the trigger rule picker. */
data class AppEntry(val pkg: String, val label: String)

/** Immutable snapshot of everything the UI renders. The single source of truth for the screen. */
data class UiState(
    val status: String = "",
    val mode: String = "Binder/privacy",
    val diag: String = "",
    val red: Float = 255f,
    val green: Float = 0f,
    val blue: Float = 0f,
    val brightness: Float = 100f,
    val periodMs: Float = 2000f,
    val animMode: String = "none",           // none / breathing / blinking
    val activePkg: String = MiuiLightController.DEFAULT_PKG,
    val rootStatus: String = "未连接",
    val takeover: Boolean = false,
    val privMode: Int = 0,          // 0 原色(stock) / 1 自定义颜色 / 2 关闭(hide)
    val privEnabled: Boolean = false,
    val privColor: Int = 0xFF00FF00.toInt(),
    val monitoring: Boolean = false,
    val eventLog: List<String> = emptyList(),
    val lastAction: String = "",
    val spectrumOn: Boolean = false,
    val sensitivity: Float = 1.0f,
    val maxBright: Float = 16f,
    val spectrumPlayback: Boolean = true,   // true 屏幕内录 / false 麦克风
    val bands: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val themeMode: Int = 0,         // 0 跟随系统 / 1 浅色 / 2 深色
    val dynamicColor: Boolean = false,
    val pickerStyle: Int = 0,       // 0 滑条 / 1 调色盘 / 2 输入色值
    val predictiveBack: Boolean = true,
    val globalMax: Float = 100f,    // 全局亮度上限 %
    val notifAccess: Boolean = false,
    val notifRules: List<NotifStore.Rule> = emptyList(),
    val notifSeen: List<NotifStore.Seen> = emptyList(),
    val notifChannels: Map<String, List<NotifStore.ChannelInfo>> = emptyMap(),
    val notifScanning: Boolean = false,
    val notifScanMsg: String = "",
    val triggers: List<TriggerStore.Rule> = emptyList(),
    val usageAccess: Boolean = false,
    val triggerMsg: String = "",
    val installedApps: List<AppEntry> = emptyList(),
)

/** Small SharedPreferences wrapper so the user's last color/animation settings survive restarts. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hyperglow_prefs", Context.MODE_PRIVATE)

    fun apply(state: UiState): UiState = state.copy(
        red = sp.getFloat("red", state.red),
        green = sp.getFloat("green", state.green),
        blue = sp.getFloat("blue", state.blue),
        brightness = sp.getFloat("brightness", state.brightness),
        periodMs = sp.getFloat("period", state.periodMs),
        sensitivity = sp.getFloat("sensitivity", state.sensitivity),
        maxBright = sp.getFloat("maxBright", state.maxBright),
        activePkg = sp.getString("pkg", state.activePkg) ?: state.activePkg,
        privEnabled = sp.getBoolean("privEnabled", state.privEnabled),
        privColor = sp.getInt("privColor", state.privColor),
        spectrumPlayback = sp.getBoolean("spectrumPlayback", state.spectrumPlayback),
        themeMode = sp.getInt("themeMode", state.themeMode),
        dynamicColor = sp.getBoolean("dynamicColor", state.dynamicColor),
        pickerStyle = sp.getInt("pickerStyle", state.pickerStyle),
        predictiveBack = sp.getBoolean("predictiveBack", state.predictiveBack),
        globalMax = sp.getFloat("globalMax", state.globalMax),
    )

    /** All persisted fields as JSON (used by 备份与恢复). */
    fun toJson(s: UiState): String = org.json.JSONObject().apply {
        put("red", s.red.toDouble()); put("green", s.green.toDouble()); put("blue", s.blue.toDouble())
        put("brightness", s.brightness.toDouble()); put("period", s.periodMs.toDouble())
        put("sensitivity", s.sensitivity.toDouble()); put("maxBright", s.maxBright.toDouble())
        put("pkg", s.activePkg)
        put("privEnabled", s.privEnabled); put("privMode", s.privMode); put("privColor", s.privColor)
        put("spectrumPlayback", s.spectrumPlayback)
        put("themeMode", s.themeMode); put("dynamicColor", s.dynamicColor)
        put("pickerStyle", s.pickerStyle); put("globalMax", s.globalMax.toDouble())
        put("predictiveBack", s.predictiveBack)
    }.toString(2)

    /** Parse a backup JSON over [base]; unknown/missing keys keep their current value. */
    fun fromJson(json: String, base: UiState): UiState? = try {
        val o = org.json.JSONObject(json)
        base.copy(
            red = o.optDouble("red", base.red.toDouble()).toFloat(),
            green = o.optDouble("green", base.green.toDouble()).toFloat(),
            blue = o.optDouble("blue", base.blue.toDouble()).toFloat(),
            brightness = o.optDouble("brightness", base.brightness.toDouble()).toFloat(),
            periodMs = o.optDouble("period", base.periodMs.toDouble()).toFloat(),
            sensitivity = o.optDouble("sensitivity", base.sensitivity.toDouble()).toFloat(),
            maxBright = o.optDouble("maxBright", base.maxBright.toDouble()).toFloat(),
            activePkg = o.optString("pkg", base.activePkg),
            privEnabled = o.optBoolean("privEnabled", base.privEnabled),
            privMode = o.optInt("privMode", base.privMode),
            privColor = o.optInt("privColor", base.privColor),
            spectrumPlayback = o.optBoolean("spectrumPlayback", base.spectrumPlayback),
            themeMode = o.optInt("themeMode", base.themeMode),
            dynamicColor = o.optBoolean("dynamicColor", base.dynamicColor),
            pickerStyle = o.optInt("pickerStyle", base.pickerStyle),
            predictiveBack = o.optBoolean("predictiveBack", base.predictiveBack),
            globalMax = o.optDouble("globalMax", base.globalMax.toDouble()).toFloat(),
        )
    } catch (e: Exception) {
        null
    }

    fun save(s: UiState) {
        sp.edit()
            .putFloat("red", s.red).putFloat("green", s.green).putFloat("blue", s.blue)
            .putFloat("brightness", s.brightness).putFloat("period", s.periodMs)
            .putFloat("sensitivity", s.sensitivity).putFloat("maxBright", s.maxBright)
            .putString("pkg", s.activePkg)
            .putBoolean("privEnabled", s.privEnabled)
            .putInt("privColor", s.privColor)
            .putBoolean("spectrumPlayback", s.spectrumPlayback)
            .putInt("themeMode", s.themeMode)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putInt("pickerStyle", s.pickerStyle)
            .putBoolean("predictiveBack", s.predictiveBack)
            .putFloat("globalMax", s.globalMax)
            .apply()
    }
}

/**
 * Owns all screen state and orchestrates [LightController] off the main thread via coroutines.
 * Survives configuration changes, so animations/spectrum keep running across rotation; teardown
 * happens in [onCleared] (only when the Activity is truly finishing).
 */
class HyperGlowViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val prefs = Prefs(app)
    private var stopEvents: (() -> Unit)? = null

    init {
        LightController.init(app)
        // restore saved settings, then backfill real system/root state — all off the main thread
        _ui.update { prefs.apply(it) }
        LightController.setPkg(_ui.value.activePkg)
        LightController.brightnessCap = _ui.value.globalMax / 100f
        viewModelScope.launch {
            val status = io { LightController.getStatus() }
            val privMode = io { LightController.readPrivacyMode() }
            _ui.update {
                it.copy(
                    status = status,
                    mode = LightController.mode(),
                    privMode = privMode,
                    privEnabled = it.privEnabled || privMode != 0,
                    rootStatus = if (LightController.rootReady) "已连接 (sysfs)" else "未连接",
                    takeover = LightController.takeoverActive,
                )
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun colorInt(s: UiState): Int =
        composeColor(s.red.toInt(), s.green.toInt(), s.blue.toInt(), s.brightness.toInt())

    private fun persist() = prefs.save(_ui.value)

    private fun refreshStatus() = viewModelScope.launch {
        val st = io { LightController.getStatus() }
        _ui.update { it.copy(status = st, mode = LightController.mode()) }
    }

    // ---- connection / diagnostics ----
    fun reconnect() = viewModelScope.launch {
        io { LightController.init(getApplication()) }
        refreshStatus()
    }

    fun diagnose() = viewModelScope.launch {
        val d = io { LightController.diagnose() }
        _ui.update { it.copy(diag = d) }
    }

    fun connectRoot() {
        _ui.update { it.copy(rootStatus = "连接中...") }
        LightController.connectRoot { ok ->
            _ui.update { it.copy(rootStatus = if (ok) "已连接 (sysfs)" else "失败: 请在 KSU 授予 root") }
            refreshStatus()
        }
    }

    fun disconnectRoot() = viewModelScope.launch {
        io { LightController.disconnectRoot() }
        _ui.update { it.copy(rootStatus = "未连接", animMode = "none") }
        refreshStatus()
    }

    fun setTakeover(enabled: Boolean) {
        _ui.update { it.copy(takeover = enabled) }
        viewModelScope.launch {
            val ok = io { LightController.setTakeover(enabled) }
            if (enabled && !ok) _ui.update { it.copy(takeover = false) }
            refreshStatus()
        }
    }

    // ---- color / animation ----
    fun setRed(v: Float) = _ui.update { it.copy(red = v) }
    fun setGreen(v: Float) = _ui.update { it.copy(green = v) }
    fun setBlue(v: Float) = _ui.update { it.copy(blue = v) }
    fun setBrightness(v: Float) = _ui.update { it.copy(brightness = v) }
    fun setPeriod(v: Float) = _ui.update { it.copy(periodMs = v) }

    fun applyPreset(r: Int, g: Int, b: Int) {
        _ui.update { it.copy(red = r.toFloat(), green = g.toFloat(), blue = b.toFloat(),
            spectrumOn = false, animMode = "none") }
        val c = colorInt(_ui.value)
        viewModelScope.launch { io { LightController.setColor(c) } }
        persist()
    }

    fun setConstant() {
        _ui.update { it.copy(spectrumOn = false, animMode = "none") }
        val c = colorInt(_ui.value)
        viewModelScope.launch { io { LightController.setColor(c) } }
        persist()
    }

    fun turnOff() {
        _ui.update { it.copy(spectrumOn = false, animMode = "none") }
        viewModelScope.launch { io { LightController.off() } }
    }

    /** Force-stop the scoped app so LSPosed reloads the hook (LSPosed-style "restart scope"). */
    fun restartScope() {
        _ui.update { it.copy(lastAction = "正在重启作用域…") }
        viewModelScope.launch {
            val ok = io { LightController.restartScope() }
            _ui.update {
                it.copy(lastAction = if (ok) "已重启作用域 · com.miui.securitycenter" else "重启作用域失败 · 需要 Root")
            }
            refreshStatus()
        }
    }

    /** Test pattern: full-white, full-brightness, 2 s sine (raised-cosine) breathing. */
    fun testLight() {
        _ui.update { it.copy(spectrumOn = false, animMode = "breathing") }
        viewModelScope.launch { io { LightController.startBreathing(0xFFFFFFFF.toInt(), 2000L) } }
    }

    fun toggleBreathing() {
        val breathing = _ui.value.animMode == "breathing"
        _ui.update { it.copy(spectrumOn = false) }
        val s = _ui.value
        val c = colorInt(s); val period = s.periodMs.toLong()
        viewModelScope.launch {
            if (breathing) { io { LightController.stopAnimation() }; _ui.update { it.copy(animMode = "none") } }
            else { io { LightController.startBreathing(c, period) }; _ui.update { it.copy(animMode = "breathing") } }
        }
        persist()
    }

    fun toggleBlink() {
        val blinking = _ui.value.animMode == "blinking"
        _ui.update { it.copy(spectrumOn = false) }
        val s = _ui.value
        val c = colorInt(s); val period = s.periodMs.toLong()
        viewModelScope.launch {
            if (blinking) { io { LightController.stopAnimation() }; _ui.update { it.copy(animMode = "none") } }
            else { io { LightController.startBlink(c, period) }; _ui.update { it.copy(animMode = "blinking") } }
        }
        persist()
    }

    /** Set the standing colour from an ARGB value and re-apply the current effect. */
    fun setManualColor(color: Int) {
        _ui.update {
            it.copy(
                red = ((color shr 16) and 0xFF).toFloat(),
                green = ((color shr 8) and 0xFF).toFloat(),
                blue = (color and 0xFF).toFloat(),
            )
        }
        persist()
        applyManual()
    }

    /** 0 常亮 / 1 呼吸 / 2 闪烁 */
    fun setEffectIndex(index: Int) {
        _ui.update {
            it.copy(
                animMode = when (index) { 1 -> "breathing"; 2 -> "blinking"; else -> "none" },
                spectrumOn = false,
            )
        }
        persist()
        applyManual()
    }

    /** Push the current colour + effect + period to the LED. */
    fun applyManual() {
        val st = _ui.value
        val c = colorInt(st)
        viewModelScope.launch {
            io {
                when (st.animMode) {
                    "breathing" -> LightController.startBreathing(c, st.periodMs.toLong())
                    "blinking" -> LightController.startBlink(c, st.periodMs.toLong())
                    else -> LightController.setColor(c)
                }
            }
            refreshStatus()
        }
    }

    // ---- music spectrum ----
    /** Microphone-sourced spectrum (RECORD_AUDIO must already be granted). */
    fun startSpectrum() = viewModelScope.launch {
        val s = _ui.value
        val ok = io {
            LightController.startSpectrum(
                s.sensitivity, s.maxBright / 100f, SpectrumAnalyzer.Source.MIC, null
            )
        }
        _ui.update { it.copy(spectrumOn = ok) }
        if (ok) pollBands()
        refreshStatus()
    }

    /**
     * Playback-capture spectrum. The consent result is handed to the foreground service, which must
     * be a foreground mediaProjection service before it may create the projection (Android 14+).
     */
    fun startSpectrumPlayback(resultCode: Int, data: Intent) {
        val s = _ui.value
        LightForegroundService.startPlaybackSpectrum(
            getApplication(), resultCode, data, s.sensitivity, s.maxBright / 100f
        )
        _ui.update { it.copy(spectrumOn = true) }
        viewModelScope.launch {
            delay(700)   // let the service create the projection and start the analyzer
            val active = LightController.spectrumActive
            _ui.update { it.copy(spectrumOn = active) }
            if (active) pollBands()
            refreshStatus()
        }
    }

    fun setSpectrumPlayback(playback: Boolean) {
        _ui.update { it.copy(spectrumPlayback = playback) }
        persist()
    }

    /** Mirror the analyzer's band levels into UI state while the spectrum runs (~25 fps). */
    private fun pollBands() = viewModelScope.launch {
        while (LightController.spectrumActive) {
            _ui.update { it.copy(bands = LightController.bands) }
            delay(40)
        }
        _ui.update { it.copy(bands = Triple(0f, 0f, 0f)) }
    }

    fun stopSpectrum() = viewModelScope.launch {
        io { LightController.stopSpectrum() }
        LightForegroundService.stop(getApplication())
        _ui.update { it.copy(spectrumOn = false, bands = Triple(0f, 0f, 0f)) }
        refreshStatus()
    }

    fun onSpectrumDenied() = _ui.update { it.copy(spectrumOn = false) }

    fun setSensitivity(v: Float) {
        _ui.update { it.copy(sensitivity = v) }
        LightController.setSpectrumSensitivity(v)
        persist()
    }

    fun setMaxBright(v: Float) {
        _ui.update { it.copy(maxBright = v) }
        LightController.setSpectrumMaxLevel(v / 100f)
        persist()
    }

    // ---- package experiment ----
    fun selectPkg(pkg: String) {
        _ui.update { it.copy(activePkg = pkg, animMode = "none") }
        val c = colorInt(_ui.value)
        viewModelScope.launch { io { LightController.setPkg(pkg); LightController.setColor(c) } }
        persist()
    }

    // ---- privacy light ----
    fun setPrivacyMode(mode: Int) {
        _ui.update { it.copy(privMode = mode) }
        viewModelScope.launch { io { LightController.setPrivacyMode(mode) } }
    }

    /**
     * Master switch for privacy-light takeover. Turning it off restores the stock policy; turning
     * it on re-applies whichever sub-option was last chosen.
     */
    fun setPrivacyEnabled(on: Boolean) {
        _ui.update { it.copy(privEnabled = on) }
        val mode = if (on) _ui.value.privMode else 0
        viewModelScope.launch { io { LightController.setPrivacyMode(mode) } }
        persist()
    }

    /** Sub-option index: 0 原色(绿) / 1 关闭 / 2 自定义颜色 -> hook modes 0 / 2 / 1. */
    fun setPrivacyOption(index: Int) {
        val mode = when (index) { 1 -> 2; 2 -> 1; else -> 0 }
        _ui.update { it.copy(privMode = mode) }
        viewModelScope.launch {
            io { LightController.setPrivacyMode(mode) }
            if (mode == 1) io { LightController.setPrivacyColor(_ui.value.privColor) }
        }
        persist()
    }

    fun setPrivacyColor(color: Int) {
        _ui.update { it.copy(privColor = color) }
        viewModelScope.launch { io { LightController.setPrivacyColor(color) } }
        persist()
    }

    // ---- notifications ----

    /** Re-read the listener grant + stored rules; call whenever the 通知 page becomes visible. */
    fun refreshNotif() {
        val ctx = getApplication<Application>()
        val enabled = try {
            android.provider.Settings.Secure.getString(
                ctx.contentResolver, "enabled_notification_listeners"
            )?.contains(ctx.packageName) == true
        } catch (e: Exception) {
            false
        }
        _ui.update {
            it.copy(
                notifAccess = enabled,
                notifRules = NotifStore.rules(ctx),
                notifSeen = NotifStore.seen(ctx),
            )
        }
    }

    /**
     * Enumerate every app's notification channels from the system policy file (needs root). The
     * public listener API for this is limited to companion-device listeners / the notification
     * assistant, so root is the practical route here.
     */
    fun scanNotifChannels() {
        if (_ui.value.notifScanning) return
        _ui.update { it.copy(notifScanning = true, notifScanMsg = "正在读取系统通知配置…") }
        viewModelScope.launch {
            val res = io { NotifStore.loadAllChannels() }
            _ui.update {
                it.copy(
                    notifChannels = res.channels,
                    notifScanning = false,
                    notifScanMsg = res.message,
                )
            }
        }
    }

    fun saveNotifRule(rule: NotifStore.Rule) {
        NotifStore.upsert(getApplication(), rule)
        refreshNotif()
    }

    fun deleteNotifRule(pkg: String, channelId: String) {
        NotifStore.remove(getApplication(), pkg, channelId)
        refreshNotif()
    }

    fun clearNotifSeen() {
        NotifStore.clearSeen(getApplication())
        refreshNotif()
    }

    /** Flash the rule's effect so the user can see it without waiting for a real notification. */
    fun previewNotifRule(color: Int, effect: Int) {
        viewModelScope.launch { io { LightController.previewNotificationEffect(color, effect) } }
    }

    // ---- custom triggers ----

    fun refreshTriggers() {
        val ctx = getApplication<Application>()
        val list = TriggerStore.rules(ctx)
        _ui.update {
            it.copy(
                triggers = list,
                usageAccess = TriggerService.hasUsageAccess(ctx),
                triggerMsg = "已保存 ${list.size} 条规则",
            )
        }
    }

    /** Launchable apps, sorted by name. Loaded lazily — this touches every installed package. */
    fun loadInstalledApps() {
        if (_ui.value.installedApps.isNotEmpty()) return
        viewModelScope.launch {
            val apps = io {
                val pm = getApplication<Application>().packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                runCatching {
                    pm.queryIntentActivities(intent, 0)
                        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                        .distinctBy { it.pkg }
                        .sortedBy { it.label }
                }.getOrDefault(emptyList())
            }
            _ui.update { it.copy(installedApps = apps) }
        }
    }

    fun saveTrigger(rule: TriggerStore.Rule) {
        val ctx = getApplication<Application>()
        TriggerStore.upsert(ctx, rule)
        refreshTriggers()          // reflect the save even if the service cannot start
        syncTriggerService(ctx)
    }

    fun deleteTrigger(id: Int) {
        val ctx = getApplication<Application>()
        TriggerStore.remove(ctx, id)
        refreshTriggers()
        syncTriggerService(ctx)
    }

    /**
     * Start/stop the listener. Kept off the save path: a foreground-service start can fail (e.g.
     * background start restrictions) and that must not make a saved rule look lost.
     */
    private fun syncTriggerService(ctx: Application) {
        try {
            TriggerService.sync(ctx)
        } catch (e: Exception) {
            _ui.update { it.copy(triggerMsg = "规则已保存，但监听服务启动失败：${e.message}") }
        }
    }

    fun newTriggerId(): Int = TriggerStore.nextId(getApplication())

    fun previewTrigger(color: Int, effect: Int) {
        viewModelScope.launch { io { LightController.previewNotificationEffect(color, effect) } }
    }

    // ---- settings ----
    fun setThemeMode(mode: Int) { _ui.update { it.copy(themeMode = mode) }; persist() }
    fun setDynamicColor(on: Boolean) { _ui.update { it.copy(dynamicColor = on) }; persist() }
    fun setPickerStyle(style: Int) { _ui.update { it.copy(pickerStyle = style) }; persist() }
    fun setPredictiveBack(on: Boolean) { _ui.update { it.copy(predictiveBack = on) }; persist() }

    /** Global brightness ceiling; re-applies the current colour so the change is visible at once. */
    fun setGlobalMax(pct: Float) {
        _ui.update { it.copy(globalMax = pct) }
        LightController.brightnessCap = pct / 100f
        persist()
        viewModelScope.launch { io { LightController.reapply() } }
    }

    /** Serialise every stored preference to JSON for backup. */
    fun exportConfig(): String = prefs.toJson(_ui.value)

    /** Restore from a backup JSON; returns false if it could not be parsed. */
    fun importConfig(json: String): Boolean {
        val restored = prefs.fromJson(json, _ui.value) ?: return false
        _ui.update { restored }
        prefs.save(restored)
        LightController.brightnessCap = restored.globalMax / 100f
        LightController.setPkg(restored.activePkg)
        viewModelScope.launch {
            io { LightController.setPrivacyMode(if (restored.privEnabled) restored.privMode else 0) }
            io { LightController.setPrivacyColor(restored.privColor) }
            io { LightController.reapply() }
            refreshStatus()
        }
        return true
    }

    // ---- event monitoring ----
    fun setMonitoring(on: Boolean) {
        _ui.update { it.copy(monitoring = on) }
        if (on) {
            stopEvents = LightController.observeEvents { list ->
                val formatted = list.map { LightController.formatEvent(it) }.take(8)
                _ui.update { st -> st.copy(eventLog = formatted) }
            }
        } else {
            stopEvents?.invoke(); stopEvents = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopEvents?.invoke()
        LightController.stopAnimation()
        LightController.stopSpectrum()
    }
}

// ---- pure color helpers (shared by the ViewModel and previews) ----
fun scaleChannel(value: Int, brightnessPct: Int): Int = (value * brightnessPct / 100).coerceIn(0, 255)

fun composeColor(r: Int, g: Int, b: Int, brightnessPct: Int): Int {
    val rr = scaleChannel(r, brightnessPct)
    val gg = scaleChannel(g, brightnessPct)
    val bb = scaleChannel(b, brightnessPct)
    return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
}
