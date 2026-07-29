package com.elsure.hyperglow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * miuix-themed UI (miuix-ui 0.9.3). Layout: the official NavigationRail on the left + a content
 * pane built on miuix Scaffold, whose contentWindowInsets (systemBars ∪ displayCutout) keep the
 * content clear of the status bar. Each page's title lives in the Scaffold's TopAppBar.
 * First slice: Home is fully wired; the other four pages are placeholders built out next.
 */

private val PKGS = listOf(
    "com.android.camera" to "相机（默认）",
    "com.elsure.hyperglow" to "本应用",
    "com.miui.voiceassist" to "语音助手",
    "android" to "系统",
)

private data class Preset(val r: Int, val g: Int, val b: Int)

private val PRESETS = listOf(
    Preset(255, 0, 0), Preset(255, 128, 0), Preset(255, 255, 0), Preset(0, 255, 0),
    Preset(0, 255, 255), Preset(0, 0, 255), Preset(160, 32, 240), Preset(255, 255, 255),
)

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("主页", Icons.Rounded.Home),
    Tab("频谱", Icons.Rounded.PlayArrow),
    Tab("通知", Icons.Rounded.Notifications),
    Tab("自定义", Icons.Rounded.Star),
    Tab("设置", Icons.Rounded.Settings),
)

@Composable
fun HyperGlowApp(vm: HyperGlowViewModel) {
    val st by vm.ui.collectAsState()
    val mode = remember(st.themeMode, st.dynamicColor) {
        if (st.dynamicColor) when (st.themeMode) {
            1 -> ColorSchemeMode.MonetLight
            2 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        } else when (st.themeMode) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val railState = rememberNavigationRailState()
        var showRestartDialog by remember { mutableStateOf(false) }
        // Drill-down state for the 通知 page, lifted so the top bar can act as a second page
        // (app name + back arrow) and so the system back gesture can be intercepted.
        var notifApp by rememberSaveable { mutableStateOf<String?>(null) }
        // Second-level page inside 设置 (currently only "about").
        var settingsDetail by rememberSaveable { mutableStateOf<String?>(null) }
        val ctx = LocalContext.current

        // Without this, swiping back inside the app's channel list exits the app.
        // With 预见式返回 on, the detail page follows the gesture and can be cancelled mid-swipe;
        // otherwise it is an ordinary instant back.
        var backProgress by remember { mutableFloatStateOf(0f) }
        if (st.predictiveBack) {
            PredictiveBackHandler(enabled = notifApp != null || settingsDetail != null) { progress ->
                try {
                    progress.collect { ev -> backProgress = ev.progress }
                    notifApp = null
                    settingsDetail = null
                    backProgress = 0f
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    backProgress = 0f   // gesture cancelled: snap back
                }
            }
        } else {
            BackHandler(enabled = notifApp != null || settingsDetail != null) {
                notifApp = null; settingsDetail = null
            }
        }
        BackHandler(enabled = notifApp == null && settingsDetail == null && tab != 0) { tab = 0 }

        // ONE full-screen Scaffold. The dim/scrim behind dialogs and dropdown popups is drawn by
        // the MiuixPopupHost that Scaffold provides, so anything OUTSIDE this Scaffold would stay
        // undimmed — that is why the rail lives inside it and the TopAppBar is placed by hand in
        // the content pane rather than using a second, content-only Scaffold.
        // The Scaffold is here only to provide the MiuixPopupHost (so dialog/dropdown scrims cover
        // the rail too). It deliberately consumes NO insets: each component applies its own, which
        // is what keeps the top bar exactly 52.dp when collapsed instead of 52.dp + status bar.
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(state = railState) {
                        TABS.forEachIndexed { i, t ->
                            NavigationRailItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = t.icon,
                                label = t.label
                            )
                        }
                    }
                    Column(Modifier.weight(1f).navigationBarsPadding()) {
                        // Large title that collapses into the bar on scroll (LSPosed-style).
                        // Home shows the app name; the other pages show their tab name.
                        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
                        val detailApp = if (tab == 2) notifApp else null
                        val detailSettings = if (tab == 4) settingsDetail else null
                        val barTitle = when {
                            detailApp != null -> NotifStore.appLabel(ctx, detailApp)
                            detailSettings == "about" -> "关于"
                            tab == 0 -> "HyperGlow"
                            else -> TABS[tab].label
                        }
                        TopAppBar(
                            title = barTitle,
                            largeTitle = barTitle,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = {
                                if (detailApp != null || detailSettings != null) {
                                    IconButton(onClick = { notifApp = null; settingsDetail = null }) {
                                        Icon(
                                            Icons.Rounded.ArrowBack,
                                            contentDescription = "返回",
                                            tint = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { showRestartDialog = true }) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = "重启作用域",
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        )
                        val zero = PaddingValues(0.dp)
                        AnimatedContent(
                            targetState = Triple(tab, notifApp, settingsDetail),
                            transitionSpec = {
                                val fromApp = initialState.second ?: initialState.third
                                val toApp = targetState.second ?: targetState.third
                                when {
                                    // entering a detail page: slide in from the right
                                    fromApp == null && toApp != null ->
                                        (slideInHorizontally(tween(280)) { it / 3 } +
                                            fadeIn(tween(220))) togetherWith
                                            fadeOut(tween(180))
                                    // leaving a detail page: slide back out to the right
                                    fromApp != null && toApp == null ->
                                        (fadeIn(tween(220))) togetherWith
                                            (slideOutHorizontally(tween(280)) { it / 3 } +
                                                fadeOut(tween(220)))
                                    // plain tab switch: a short cross-fade with a small lift
                                    else ->
                                        (slideInHorizontally(tween(240)) { it / 12 } +
                                            fadeIn(tween(200))) togetherWith fadeOut(tween(140))
                                }
                            },
                            label = "page"
                        ) { (t, app, detail) ->
                            Box(
                                Modifier.graphicsLayer {
                                    // follow the predictive-back gesture on any second-level page
                                    val p = if (app != null || detail != null) backProgress else 0f
                                    scaleX = 1f - 0.06f * p
                                    scaleY = 1f - 0.06f * p
                                    alpha = 1f - 0.25f * p
                                }
                            ) {
                                when (t) {
                                    0 -> HomePage(vm, zero, scrollBehavior.nestedScrollConnection)
                                    1 -> SpectrumPage(vm, zero, scrollBehavior.nestedScrollConnection)
                                    2 -> NotificationPage(
                                        vm, zero, scrollBehavior.nestedScrollConnection,
                                        openApp = app,
                                        onOpenApp = { notifApp = it },
                                    )
                                    3 -> TriggerPage(vm, zero, scrollBehavior.nestedScrollConnection)
                                    else -> SettingsPage(
                                        vm, zero, scrollBehavior.nestedScrollConnection,
                                        detail = detail,
                                        onOpenDetail = { settingsDetail = it },
                                    )
                                }
                            }
                        }
                    }
                }

                OverlayDialog(
                    title = "重启作用域",
                    summary = "将强制停止 com.miui.securitycenter，使 LSPosed 重新注入 Hook。" +
                            "安全中心会自行重启，期间隐私灯可能短暂闪烁。",
                    show = showRestartDialog,
                    onDismissRequest = { showRestartDialog = false }
                ) {
                    Row {
                        TextButton(
                            text = "取消",
                            onClick = { showRestartDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "重启",
                            onClick = { showRestartDialog = false; vm.restartScope() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val s by vm.ui.collectAsState()
    val cs = MiuixTheme.colorScheme
    var showColorDialog by remember { mutableStateOf(false) }
    ColorPickerDialog(
        show = showColorDialog,
        title = "隐私灯颜色",
        initial = s.privColor,
        pickerStyle = s.pickerStyle,
        onColorChanged = { vm.setPrivacyColor(it) },
        onDismiss = { showColorDialog = false },
    )
    Column(
        Modifier
            .fillMaxSize()
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ---- hero + status, side by side ----
        val working = s.takeover
        val accent = if (working) Color(0xFF2AA84A) else cs.primary
        val heroTitle = when {
            working -> "已完全接管"
            s.rootStatus.startsWith("已连接") -> "Root 已就绪"
            else -> "灯效就绪"
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(172.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(accent)
                    .padding(20.dp)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        if (working) Icons.Rounded.Star else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Column {
                        Text(
                            heroTitle,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "模式 · ${s.mode}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Card(
                Modifier
                    .weight(1f)
                    .height(172.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                    InfoRow("连接后端", s.status)
                    InfoRow("Root", s.rootStatus)
                    InfoRow(
                        "隐私灯",
                        when { !s.privEnabled -> "未接管"
                               s.privMode == 1 -> "自定义"
                               s.privMode == 2 -> "隐藏"
                               else -> "原色" }
                    )
                }
            }
        }
        if (s.lastAction.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) { InfoRow("上次操作", s.lastAction) }
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("工作模式")
        Card(Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "完全接管",
                summary = "Root 守护进程接管澎湃灯，压制相机隐私灯覆盖",
                checked = s.takeover,
                onCheckedChange = { vm.setTakeover(it) }
            )
            SwitchPreference(
                title = "隐私灯接管",
                summary = "接管相机隐私指示灯，交由下方选项决定表现",
                checked = s.privEnabled,
                onCheckedChange = { vm.setPrivacyEnabled(it) }
            )
            if (s.privEnabled) {
                // hook modes: 0 原色 / 2 关闭 / 1 自定义颜色
                val privIndex = when (s.privMode) { 2 -> 1; 1 -> 2; else -> 0 }
                OverlayDropdownPreference(
                    title = "隐私灯表现",
                    items = listOf("原色（绿）", "关闭", "自定义颜色"),
                    selectedIndex = privIndex,
                    onSelectedIndexChange = { vm.setPrivacyOption(it) }
                )
                if (s.privMode == 1) {
                    ArrowPreference(
                        title = "自定义颜色",
                        summary = "#%06X".format(s.privColor and 0xFFFFFF),
                        onClick = { showColorDialog = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("测试")
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "测试灯光",
                summary = "全彩全亮度 · 2 秒正弦呼吸",
                onClick = { vm.testLight() }
            )
            ArrowPreference(title = "关闭指示灯", onClick = { vm.turnOff() })
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = cs.onSurface, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = cs.onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PlaceholderPage(contentPadding: PaddingValues, title: String, subtitle: String) {
    val cs = MiuixTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = cs.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = cs.onSurface.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("本页开发中", color = cs.primary, fontSize = 13.sp)
    }
}

/** ARGB -> (hue 0..360, saturation 0..1). Value/brightness is intentionally discarded. */
private fun rgbToHsv(color: Int): Pair<Float, Float> {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> (60f * (((g - b) / d) % 6f) + 360f) % 360f
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val sv = if (max == 0f) 0f else d / max
    return h to sv
}

/** (hue, saturation) -> full-brightness ARGB. Value is pinned to 1.0 so no dark colours exist. */
private fun hsvToRgb(hue: Float, sat: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = sat.coerceIn(0f, 1f)
    val c = s                      // value is always 1.0
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = 1f - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = (((r1 + m) * 255f).toInt()).coerceIn(0, 255)
    val g = (((g1 + m) * 255f).toInt()).coerceIn(0, 255)
    val b = (((b1 + m) * 255f).toInt()).coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

@Composable
private fun SpectrumPage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startSpectrum() else vm.onSpectrumDenied() }

    // Screen-capture consent; the result is handed to the foreground service, which creates the
    // projection itself (Android 14+ requires the FGS to be running first).
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            vm.startSpectrumPlayback(result.resultCode, data)
        } else {
            vm.onSpectrumDenied()
        }
    }

    fun begin() {
        if (s.spectrumPlayback) {
            val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } else {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) vm.startSpectrum() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        BandMeter(s.bands, s.spectrumOn)

        Spacer(Modifier.height(16.dp))
        SmallTitle("音乐律动")
        Card(Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "随音乐律动",
                summary = if (s.spectrumPlayback) "捕获播放的音频流，耳机播放同样有效"
                          else "通过麦克风拾音，耳机播放无法捕获",
                checked = s.spectrumOn,
                onCheckedChange = { on -> if (on) begin() else vm.stopSpectrum() }
            )
            OverlayDropdownPreference(
                title = "音源",
                items = listOf("屏幕内录", "麦克风"),
                selectedIndex = if (s.spectrumPlayback) 0 else 1,
                onSelectedIndexChange = { idx ->
                    if (s.spectrumOn) vm.stopSpectrum()
                    vm.setSpectrumPlayback(idx == 0)
                },
                enabled = !s.spectrumOn
            )
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("调节")
        Card(Modifier.fillMaxWidth()) {
            SliderPreference(
                title = "灵敏度",
                summary = "越高越容易到达亮度上限",
                value = s.sensitivity,
                onValueChange = { vm.setSensitivity(it) },
                valueRange = 0.2f..2.5f,
                valueText = "%.1f×".format(s.sensitivity)
            )
            SliderPreference(
                title = "亮度上限",
                summary = "灯光最亮时的强度，避免刺眼",
                value = s.maxBright,
                onValueChange = { vm.setMaxBright(it) },
                valueRange = 2f..40f,
                valueText = "${s.maxBright.toInt()}%"
            )
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            InfoRow("需要 Root", "频谱经由守护进程驱动，需授予 Root")
            InfoRow("音源说明", if (s.spectrumPlayback) "部分应用可拒绝被内录，此时为静音" else "其他应用占用麦克风时会被暂时抢占")
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** Three-band live meter: bass / mid / treble, coloured like the LED output. */
@Composable
private fun BandMeter(bands: Triple<Float, Float, Float>, active: Boolean) {
    val cs = MiuixTheme.colorScheme
    val labels = listOf("低频", "中频", "高频")
    val colors = listOf(Color(0xFFFF4D4D), Color(0xFF4DD964), Color(0xFF4D9BFF))
    val values = listOf(bands.first, bands.second, bands.third)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                values.forEachIndexed { i, raw ->
                    val level by animateFloatAsState(
                        targetValue = if (active) raw.coerceIn(0f, 1f) else 0f,
                        label = "band$i"
                    )
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cs.onSurface.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(level.coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors[i])
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(labels[i], color = cs.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
            if (!active) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "未运行 · 打开下方开关开始",
                    color = cs.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    detail: String? = null,
    onOpenDetail: (String?) -> Unit = {},
) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current
    var restoreMsg by remember { mutableStateOf("") }

    if (detail == "about") {
        AboutPage(contentPadding, scrollBehavior)
        return
    }

    // Backup / restore go through the storage picker, so no storage permission is needed.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            restoreMsg = try {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(vm.exportConfig().toByteArray())
                }
                "已导出配置"
            } catch (e: Exception) {
                "导出失败：${e.message}"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreMsg = try {
                val text = ctx.contentResolver.openInputStream(uri)
                    ?.use { String(it.readBytes()) } ?: ""
                if (vm.importConfig(text)) "已恢复配置" else "文件格式无法识别"
            } catch (e: Exception) {
                "恢复失败：${e.message}"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        SmallTitle("外观")
        Card(Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "主题",
                items = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = s.themeMode,
                onSelectedIndexChange = { vm.setThemeMode(it) }
            )
            SwitchPreference(
                title = "预见式返回",
                summary = "返回手势可实时预览并中途取消（应用内二级页面）",
                checked = s.predictiveBack,
                onCheckedChange = { vm.setPredictiveBack(it) }
            )
            SwitchPreference(
                title = "动态取色",
                summary = "跟随系统壁纸生成配色（Monet）",
                checked = s.dynamicColor,
                onCheckedChange = { vm.setDynamicColor(it) }
            )
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("颜色")
        Card(Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "取色方式",
                summary = "选择颜色时使用的交互",
                items = listOf("滑条（色相 / 饱和度）", "调色盘", "输入色值"),
                selectedIndex = s.pickerStyle,
                onSelectedIndexChange = { vm.setPickerStyle(it) }
            )
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("亮度")
        Card(Modifier.fillMaxWidth()) {
            SliderPreference(
                title = "全局亮度上限",
                summary = "所有灯效的亮度天花板；颜色只决定色相，亮度由这里统一控制",
                value = s.globalMax,
                onValueChange = { vm.setGlobalMax(it) },
                valueRange = 5f..100f,
                valueText = "${s.globalMax.toInt()}%"
            )
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("配置")
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "备份配置",
                summary = "导出为 JSON 文件",
                onClick = { exportLauncher.launch("hyperglow-config.json") }
            )
            ArrowPreference(
                title = "恢复配置",
                summary = "从 JSON 文件导入并立即应用",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
            if (restoreMsg.isNotEmpty()) InfoRow("结果", restoreMsg)
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("调试")
        Card(Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "Binder 包名",
                summary = "无 Root 时经隐私灯通道点灯所用的调用方标识",
                items = PKGS.map { it.second },
                selectedIndex = PKGS.indexOfFirst { it.first == s.activePkg }.coerceAtLeast(0),
                onSelectedIndexChange = { vm.selectPkg(PKGS[it].first) }
            )
            SwitchPreference(
                title = "灯光事件监听",
                summary = "显示 Hook 上报的系统灯光调用",
                checked = s.monitoring,
                onCheckedChange = { vm.setMonitoring(it) }
            )
            ArrowPreference(title = "读取诊断信息", onClick = { vm.diagnose() })
        }
        if (s.monitoring) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                if (s.eventLog.isEmpty()) {
                    InfoRow("暂无事件", "打开相机试试")
                } else {
                    s.eventLog.forEach { line ->
                        Text(
                            line,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        if (s.diag.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(
                    s.diag,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(title = "关于", onClick = { onOpenDetail("about") })
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NotificationPage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    openApp: String? = null,
    onOpenApp: (String?) -> Unit = {},
) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<NotifStore.Rule?>(null) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshNotif() }

    val scroll = Modifier
        .fillMaxSize()
        .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)

    val app = openApp
    if (app != null) {
        // ---- second level: one app's channels ----
        val channels = s.notifChannels[app].orEmpty()
        Column(scroll) {
            Spacer(Modifier.height(4.dp))
            Card(Modifier.fillMaxWidth()) {
                InfoRow("包名", app)
                InfoRow("通知类别", "${s.notifChannels[app]?.size ?: 0} 个")
            }
            Spacer(Modifier.height(10.dp))
            SmallTitle("通知类别")
            Card(Modifier.fillMaxWidth()) {
                val appDefault = s.notifRules.firstOrNull {
                    it.pkg == app && it.channelId == NotifStore.ANY_CHANNEL
                }
                ArrowPreference(
                    title = "全部类别（默认）",
                    summary = appDefault?.let {
                        "${NotifStore.effectName(it.effect)} · ${if (it.durationSec > 0) "${it.durationSec}秒" else "常亮"}"
                    } ?: "未配置",
                    onClick = {
                        editing = appDefault ?: NotifStore.Rule(pkg = app)
                    }
                )
                if (channels.isEmpty()) {
                    InfoRow("无类别信息", "点击上方「扫描通知类别」后再试")
                } else {
                    channels.forEach { ch ->
                        val rule = s.notifRules.firstOrNull {
                            it.pkg == app && it.channelId == ch.id
                        }
                        ArrowPreference(
                            title = ch.name,
                            summary = rule?.let {
                                "${NotifStore.effectName(it.effect)} · ${if (it.durationSec > 0) "${it.durationSec}秒" else "常亮"}"
                            } ?: ch.id,
                            onClick = {
                                editing = rule ?: NotifStore.Rule(pkg = app, channelId = ch.id)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    } else {
        // ---- first level: permission, rules, app list ----
        Column(scroll) {
            Spacer(Modifier.height(4.dp))
            SmallTitle("通知使用权")
            Card(Modifier.fillMaxWidth()) {
                InfoRow("状态", if (s.notifAccess) "已授权" else "未授权")
                ArrowPreference(
                    title = if (s.notifAccess) "重新配置" else "去授权",
                    summary = "需要通知使用权才能读取通知并触发灯效",
                    onClick = {
                        ctx.startActivity(
                            android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            SmallTitle("已配置的规则")
            Card(Modifier.fillMaxWidth()) {
                if (s.notifRules.isEmpty()) {
                    InfoRow("暂无规则", "在下方选择应用后添加")
                } else {
                    s.notifRules.forEach { r ->
                        SwitchPreference(
                            title = NotifStore.appLabel(ctx, r.pkg),
                            summary = "${if (r.channelId.isEmpty()) "全部类别" else r.channelId} · " +
                                    "${NotifStore.effectName(r.effect)} · ${if (r.durationSec > 0) "${r.durationSec}秒" else "常亮"}",
                            checked = r.enabled,
                            onCheckedChange = { vm.saveNotifRule(r.copy(enabled = it)) }
                        )
                    }
                }
                ArrowPreference(
                    title = "通用规则",
                    summary = "对所有未单独配置的通知生效",
                    onClick = {
                        editing = s.notifRules.firstOrNull { it.pkg == NotifStore.ANY_APP }
                            ?: NotifStore.Rule(pkg = NotifStore.ANY_APP)
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            SmallTitle("按应用配置")
            Card(Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = if (s.notifScanning) "扫描中…" else "扫描通知类别",
                    summary = "通过 Root 读取系统配置，枚举所有应用的通知类别",
                    onClick = { vm.scanNotifChannels() }
                )
                if (s.notifScanMsg.isNotEmpty()) InfoRow("结果", s.notifScanMsg)
            }

            if (s.notifChannels.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        TextField(
                            value = filter,
                            onValueChange = { filter = it },
                            label = "搜索应用",
                            singleLine = true
                        )
                    }
                    // Long list inside a scrolling Column: filter first, then cap, so we never
                    // lay out hundreds of rows at once.
                    val entries = s.notifChannels.entries
                        .map { it.key to NotifStore.appLabel(ctx, it.key) }
                        .filter {
                            filter.isBlank() || it.second.contains(filter, true) ||
                                    it.first.contains(filter, true)
                        }
                        .sortedBy { it.second }
                    entries.take(40).forEach { (pkg, label) ->
                        val count = s.notifChannels[pkg]?.size ?: 0
                        ArrowPreference(
                            title = label,
                            summary = "$count 个通知类别",
                            onClick = { onOpenApp(pkg) }
                        )
                    }
                    if (entries.size > 40) {
                        InfoRow("仅显示前 40 个", "共 ${entries.size} 个，请用搜索缩小范围")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SmallTitle("最近收到的通知")
            Card(Modifier.fillMaxWidth()) {
                if (s.notifSeen.isEmpty()) {
                    InfoRow("暂无记录", if (s.notifAccess) "收到通知后会出现在这里" else "请先授予通知使用权")
                } else {
                    s.notifSeen.reversed().take(15).forEach { seen ->
                        ArrowPreference(
                            title = NotifStore.appLabel(ctx, seen.pkg),
                            summary = if (seen.channelId.isEmpty()) "全部类别" else seen.channelId,
                            onClick = {
                                editing = s.notifRules.firstOrNull {
                                    it.pkg == seen.pkg && it.channelId == seen.channelId
                                } ?: NotifStore.Rule(pkg = seen.pkg, channelId = seen.channelId)
                            }
                        )
                    }
                    ArrowPreference(title = "清空记录", onClick = { vm.clearNotifSeen() })
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    editing?.let { rule ->
        NotifRuleDialog(
            rule = rule,
            appLabel = NotifStore.appLabel(ctx, rule.pkg),
            pickerStyle = s.pickerStyle,
            onPreview = { c, e -> vm.previewNotifRule(c, e) },
            onDelete = { vm.deleteNotifRule(rule.pkg, rule.channelId); editing = null },
            onSave = { vm.saveNotifRule(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NotifRuleDialog(
    rule: NotifStore.Rule,
    appLabel: String,
    onPreview: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onSave: (NotifStore.Rule) -> Unit,
    onDismiss: () -> Unit,
    pickerStyle: Int,
) {
    var hue by remember(rule.key) { mutableFloatStateOf(rgbToHsv(rule.color).first) }
    var sat by remember(rule.key) { mutableFloatStateOf(rgbToHsv(rule.color).second) }
    var effect by remember(rule.key) { mutableIntStateOf(rule.effect) }
    var duration by remember(rule.key) { mutableFloatStateOf(rule.durationSec.toFloat()) }
    val color = hsvToRgb(hue, sat)

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "不保存并返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        appLabel,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (rule.channelId.isEmpty()) "全部通知类别" else "类别：${rule.channelId}",
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = {
                    onSave(
                        rule.copy(
                            enabled = true,
                            color = color,
                            effect = effect,
                            durationSec = duration.toInt()
                        )
                    )
                }) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "保存并返回",
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(color))
            )
            Spacer(Modifier.height(8.dp))
            InlineColorPicker(
                pickerStyle = pickerStyle,
                hue = hue,
                sat = sat,
                onHueSatChanged = { h, s2 -> hue = h; sat = s2 }
            )
            OverlayDropdownPreference(
                title = "灯效",
                items = listOf("常亮", "呼吸", "闪烁"),
                selectedIndex = effect,
                onSelectedIndexChange = { effect = it }
            )
            SliderPreference(
                title = "持续时间",
                summary = if (duration < 1f) "通知存续期间常亮（含常驻通知）"
                          else "结束后恢复原来的灯光",
                value = duration,
                onValueChange = { duration = it },
                valueRange = 0f..15f,
                valueText = if (duration < 1f) "常亮" else "${duration.toInt()}秒"
            )
            ArrowPreference(title = "预览", onClick = { onPreview(color, effect) })
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "删除",
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TriggerPage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    var editing by remember { mutableStateOf<TriggerStore.Rule?>(null) }
    var showManualColor by remember { mutableStateOf(false) }
    var revealedId by remember { mutableIntStateOf(-1) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    val selectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(Unit) { vm.refreshTriggers(); vm.loadInstalledApps() }
    BackHandler(enabled = selectionMode) { selectedIds.clear() }

    ColorPickerDialog(
        show = showManualColor,
        title = "灯光颜色",
        initial = composeColor(s.red.toInt(), s.green.toInt(), s.blue.toInt(), 100),
        pickerStyle = s.pickerStyle,
        onColorChanged = { vm.setManualColor(it) },
        onDismiss = { showManualColor = false },
    )

    val needsUsage = s.triggers.any {
        it.enabled && it.type == TriggerStore.T_APP_FOREGROUND
    } && !s.usageAccess

    Column(
        Modifier
            .fillMaxSize()
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        SmallTitle("灯光")
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "颜色",
                summary = "#%06X".format(
                    composeColor(s.red.toInt(), s.green.toInt(), s.blue.toInt(), 100) and 0xFFFFFF
                ),
                onClick = { showManualColor = true }
            )
            OverlayDropdownPreference(
                title = "灯效",
                items = listOf("常亮", "呼吸", "闪烁"),
                selectedIndex = when (s.animMode) { "breathing" -> 1; "blinking" -> 2; else -> 0 },
                onSelectedIndexChange = { vm.setEffectIndex(it) }
            )
            if (s.animMode != "none") {
                SliderPreference(
                    title = "周期",
                    value = s.periodMs,
                    onValueChange = { vm.setPeriod(it) },
                    onValueChangeFinished = { vm.applyManual() },
                    valueRange = 400f..5000f,
                    valueText = "${s.periodMs.toInt()}ms"
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SmallTitle("预设")
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PRESETS.forEach { p ->
                    val c = (0xFF shl 24) or (p.r shl 16) or (p.g shl 8) or p.b
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(c))
                            .clickable { vm.setManualColor(c) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (needsUsage) {
            Card(Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "需要使用情况访问权限",
                    summary = "「指定应用在前台」规则依赖它来识别当前应用",
                    onClick = {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (selectionMode) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已选 ${selectedIds.size} 项",
                        color = cs.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        text = "全选",
                        onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(s.triggers.map { it.id })
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "删除",
                        onClick = {
                            selectedIds.toList().forEach { vm.deleteTrigger(it) }
                            selectedIds.clear()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(text = "取消", onClick = { selectedIds.clear() })
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        SmallTitle("触发规则")
        // Actions live in their own card above the list, so they stay put while rules scroll.
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "新建规则",
                summary = "例如：电量低于 20% 亮红灯",
                onClick = {
                    editing = TriggerStore.Rule(
                        id = vm.newTriggerId(),
                        type = TriggerStore.T_BATTERY_LOW,
                        param = "20",
                    )
                }
            )
            if (s.triggerMsg.isNotEmpty()) InfoRow("状态", s.triggerMsg)
        }

        Spacer(Modifier.height(10.dp))
        if (s.triggers.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                InfoRow("暂无规则", "点击上方新建")
            }
        } else {
            s.triggers.forEach { r ->
                SwipeActionRow(
                    revealed = revealedId == r.id,
                    onRevealChange = { revealedId = if (it) r.id else -1 },
                    selectionMode = selectionMode,
                    onClick = {
                        when {
                            selectionMode ->
                                if (selectedIds.contains(r.id)) selectedIds.remove(r.id)
                                else selectedIds.add(r.id)
                            revealedId == r.id -> revealedId = -1
                            else -> editing = r
                        }
                    },
                    onLongClick = {
                        revealedId = -1
                        if (!selectedIds.contains(r.id)) selectedIds.add(r.id)
                    },
                    actions = {
                        SwipeActionChip(
                            icon = Icons.Rounded.Info,
                            label = "详情",
                            container = cs.primary.copy(alpha = 0.16f),
                            contentColor = cs.primary,
                            onClick = { revealedId = -1; editing = r }
                        )
                        Spacer(Modifier.width(8.dp))
                        SwipeActionChip(
                            icon = Icons.Rounded.Delete,
                            label = "删除",
                            container = Color(0xFFE04A4A),
                            contentColor = Color.White,
                            onClick = { revealedId = -1; vm.deleteTrigger(r.id) }
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedIds.contains(r.id))
                                        cs.primary.copy(alpha = 0.14f) else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(r.color))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    TriggerStore.describe(ctx, r),
                                    color = cs.onSurface,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${NotifStore.effectName(r.effect)} · " +
                                            (if (r.durationSec > 0) "${r.durationSec}秒" else "持续") +
                                            " · 优先级 ${r.priority}",
                                    color = cs.onSurface.copy(alpha = 0.55f),
                                    fontSize = 12.sp
                                )
                            }
                            if (!selectionMode) {
                                Switch(
                                    checked = r.enabled,
                                    onCheckedChange = { vm.saveTrigger(r.copy(enabled = it)) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            InfoRow("优先级", "多个条件同时满足时，数字大的规则生效")
            InfoRow("层级", "通知（瞬时） > 触发（持续） > 手动常亮")
            InfoRow("频谱", "音乐律动运行时不会被触发规则打断")
        }
        Spacer(Modifier.height(28.dp))
    }

    editing?.let { rule ->
        TriggerRuleDialog(
            rule = rule,
            apps = s.installedApps,
            pickerStyle = s.pickerStyle,
            onPreview = { c, e -> vm.previewTrigger(c, e) },
            onDelete = { vm.deleteTrigger(rule.id); editing = null },
            onSave = { vm.saveTrigger(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TriggerRuleDialog(
    rule: TriggerStore.Rule,
    apps: List<AppEntry>,
    onPreview: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onSave: (TriggerStore.Rule) -> Unit,
    onDismiss: () -> Unit,
    pickerStyle: Int,
) {
    var type by remember(rule.id) { mutableIntStateOf(rule.type) }
    var param by remember(rule.id) { mutableStateOf(rule.param) }
    var hue by remember(rule.id) { mutableFloatStateOf(rgbToHsv(rule.color).first) }
    var sat by remember(rule.id) { mutableFloatStateOf(rgbToHsv(rule.color).second) }
    var effect by remember(rule.id) { mutableIntStateOf(rule.effect) }
    var priority by remember(rule.id) { mutableFloatStateOf(rule.priority.toFloat()) }
    var duration by remember(rule.id) { mutableFloatStateOf(rule.durationSec.toFloat()) }
    val color = hsvToRgb(hue, sat)

    // Back gesture / outside tap route to onDismiss, which discards — saving is only ever the ✓.
    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "不保存并返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "触发规则",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = {
                    onSave(
                        rule.copy(
                            type = type,
                            param = param,
                            color = color,
                            effect = effect,
                            priority = priority.toInt(),
                            durationSec = duration.toInt(),
                            enabled = true,
                        )
                    )
                }) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "保存并返回",
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(color))
            )
            Spacer(Modifier.height(8.dp))
            OverlayDropdownPreference(
                title = "触发条件",
                items = TriggerStore.ALL_TYPES.map { TriggerStore.typeName(it) },
                selectedIndex = TriggerStore.ALL_TYPES.indexOf(type).coerceAtLeast(0),
                onSelectedIndexChange = { type = TriggerStore.ALL_TYPES[it] }
            )
            when (type) {
                TriggerStore.T_BATTERY_LOW -> {
                    val pct = param.toFloatOrNull() ?: 20f
                    SliderPreference(
                        title = "电量阈值",
                        value = pct,
                        onValueChange = { param = it.toInt().toString() },
                        valueRange = 5f..50f,
                        valueText = "${pct.toInt()}%"
                    )
                }
                TriggerStore.T_APP_FOREGROUND -> {
                    var expanded by remember(rule.id) { mutableStateOf(param.isBlank()) }
                    var query by remember(rule.id) { mutableStateOf("") }
                    val chosen = apps.firstOrNull { it.pkg == param }
                    ArrowPreference(
                        title = "目标应用",
                        summary = chosen?.label ?: param.ifBlank { "点击选择" },
                        onClick = { expanded = !expanded }
                    )
                    if (expanded) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "搜索应用",
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        val matches = apps.filter {
                            query.isBlank() || it.label.contains(query, true) ||
                                    it.pkg.contains(query, true)
                        }.take(12)
                        if (apps.isEmpty()) {
                            Text(
                                "正在读取已安装应用…",
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        matches.forEach { a ->
                            val sel = a.pkg == param
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (sel) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { param = a.pkg; expanded = false }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        a.label,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        a.pkg,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                                if (sel) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                else -> Unit
            }
            InlineColorPicker(
                pickerStyle = pickerStyle,
                hue = hue,
                sat = sat,
                onHueSatChanged = { h, s2 -> hue = h; sat = s2 }
            )
            OverlayDropdownPreference(
                title = "灯效",
                items = listOf("常亮", "呼吸", "闪烁"),
                selectedIndex = effect,
                onSelectedIndexChange = { effect = it }
            )
            SliderPreference(
                title = "灯效时长",
                summary = if (duration < 1f) "常亮直到事件结束（部分事件支持）"
                          else "亮起 ${duration.toInt()} 秒后自动恢复",
                value = duration,
                onValueChange = { duration = it },
                valueRange = 0f..60f,
                valueText = if (duration < 1f) "常亮" else "${duration.toInt()}秒"
            )
            SliderPreference(
                title = "优先级",
                summary = "多个条件同时满足时，数字大的优先",
                value = priority,
                onValueChange = { priority = it },
                valueRange = 1f..10f,
                valueText = priority.toInt().toString()
            )
            ArrowPreference(title = "预览", onClick = { onPreview(color, effect) })
        }
    }
}

/** Rainbow HSV wheel: hue = angle, saturation = distance from centre. */
@Composable
private fun HsvWheelPicker(
    hue: Float,
    sat: Float,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rainbow = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    }
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = minOf(size.width, size.height) / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(brush = Brush.sweepGradient(rainbow, center = Offset(cx, cy)), radius = r, center = Offset(cx, cy))
            drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
            val rad = Math.toRadians(hue.toDouble())
            val dx = (sat * r * kotlin.math.cos(rad)).toFloat()
            val dy = (sat * r * kotlin.math.sin(rad)).toFloat()
            drawCircle(Color.White, radius = 14f, center = Offset(cx + dx, cy + dy))
            drawCircle(Color.Black, radius = 14f, center = Offset(cx + dx, cy + dy), style = Stroke(4f))
            drawCircle(color = Color(hsvToRgb(hue, sat)), radius = 10f, center = Offset(cx + dx, cy + dy))
        }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, _ ->
                change.consume()
                val cx2 = size.width / 2f; val cy2 = size.height / 2f
                val dx2 = change.position.x - cx2; val dy2 = change.position.y - cy2
                val r2 = minOf(size.width, size.height) / 2f
                var a = Math.toDegrees(kotlin.math.atan2(dy2.toDouble(), dx2.toDouble())).toFloat()
                if (a < 0f) a += 360f
                onHueSatChanged(a, kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2).coerceAtMost(r2) / r2)
            }
        }.pointerInput(Unit) {
            detectTapGestures { pos ->
                val cx2 = size.width / 2f; val cy2 = size.height / 2f
                val dx2 = pos.x - cx2; val dy2 = pos.y - cy2
                val r2 = minOf(size.width, size.height) / 2f
                var a = Math.toDegrees(kotlin.math.atan2(dy2.toDouble(), dx2.toDouble())).toFloat()
                if (a < 0f) a += 360f
                onHueSatChanged(a, kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2).coerceAtMost(r2) / r2)
            }
        })
    }
}

/** Inline colour picker that respects the global pickerStyle (0=sliders, 1=wheel, 2=hex). */
@Composable
private fun InlineColorPicker(
    pickerStyle: Int,
    hue: Float,
    sat: Float,
    onHueSatChanged: (Float, Float) -> Unit,
) {
    when (pickerStyle) {
        1 -> HsvWheelPicker(
            hue = hue, sat = sat,
            onHueSatChanged = onHueSatChanged,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        2 -> {
            val preview = hsvToRgb(hue, sat)
            var hex by remember { mutableStateOf("%06X".format(preview and 0xFFFFFF)) }
            TextField(
                value = hex,
                onValueChange = { raw ->
                    hex = raw.trimStart('#').uppercase().take(6)
                    if (hex.length == 6) hex.toIntOrNull(16)?.let { rgb ->
                        val hs = rgbToHsv(rgb)
                        onHueSatChanged(hs.first, hs.second)
                    }
                },
                label = "色值 RRGGBB",
                singleLine = true
            )
        }
        else -> {
            SliderPreference(
                title = "色相",
                value = hue,
                onValueChange = { onHueSatChanged(it, sat) },
                valueRange = 0f..360f,
                valueText = "${hue.toInt()}°"
            )
            SliderPreference(
                title = "饱和度",
                value = sat,
                onValueChange = { onHueSatChanged(hue, it) },
                valueRange = 0f..1f,
                valueText = "${(sat * 100).toInt()}%"
            )
        }
    }
}

/**
 * Colour chooser shared by every "pick a colour" entry point. The interaction follows the user's
 * 取色方式 setting, and all three variants normalise to FULL value: the LED's brightness is the
 * global setting's job, so a "dark" colour would only ever mean a dim one.
 */
@Composable
private fun ColorPickerDialog(
    show: Boolean,
    title: String,
    initial: Int,
    pickerStyle: Int,
    onColorChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seeded when the dialog opens, not on every change: the controls write back through
    // onColorChanged, so keying on the value would fight the drag.
    val seed = remember(show) { rgbToHsv(initial) }
    var hue by remember(show) { mutableFloatStateOf(seed.first) }
    var sat by remember(show) { mutableFloatStateOf(seed.second) }
    val preview = hsvToRgb(hue, sat)

    OverlayDialog(
        title = title,
        summary = "只取色相与饱和度：亮度由设置页的全局亮度上限统一控制。",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(preview)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#%06X".format(preview and 0xFFFFFF),
                    color = if (sat > 0.55f) Color.White else Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            when (pickerStyle) {
                1 -> HsvWheelPicker(
                    hue = hue, sat = sat,
                    onHueSatChanged = { h, s2 ->
                        hue = h; sat = s2
                        onColorChanged(hsvToRgb(h, s2))
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                2 -> {
                    var hex by remember(show) { mutableStateOf("%06X".format(preview and 0xFFFFFF)) }
                    TextField(
                        value = hex,
                        onValueChange = { raw ->
                            hex = raw.trimStart('#').uppercase().take(6)
                            if (hex.length == 6) hex.toIntOrNull(16)?.let { rgb ->
                                val hs = rgbToHsv(rgb)
                                hue = hs.first; sat = hs.second
                                onColorChanged(hsvToRgb(hs.first, hs.second))
                            }
                        },
                        label = "色值 RRGGBB",
                        singleLine = true
                    )
                }
                else -> {
                    SliderPreference(
                        title = "色相",
                        summary = "红 → 黄 → 绿 → 青 → 蓝 → 品红",
                        value = hue,
                        onValueChange = { hue = it; onColorChanged(hsvToRgb(it, sat)) },
                        valueRange = 0f..360f,
                        valueText = "${hue.toInt()}°"
                    )
                    SliderPreference(
                        title = "饱和度",
                        summary = "越低越接近白光，100% 为纯色",
                        value = sat,
                        onValueChange = { sat = it; onColorChanged(hsvToRgb(hue, it)) },
                        valueRange = 0f..1f,
                        valueText = "${(sat * 100).toInt()}%"
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "完成",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * A list row that slides left to reveal actions, and enters multi-select on long press.
 *
 * miuix has no swipe-action component, so the gesture is handled here: a horizontal drag moves the
 * foreground over a fixed action strip. Only one row is open at a time (owned by the caller via
 * [revealed]/[onRevealChange]) so an open row closes when another is swiped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeActionRow(
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val actionsWidth = 152.dp
    val offset by animateDpAsState(
        targetValue = if (revealed && !selectionMode) -actionsWidth else 0.dp,
        label = "swipe"
    )
    var drag by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .width(actionsWidth)
                .fillMaxHeight()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
        Box(
            Modifier
                .fillMaxWidth()
                .offset(x = offset)
                // no background here: the row's own Card provides the opaque surface that hides
                // the action strip, so we never have to guess the theme's card colour
                .pointerInput(selectionMode) {
                    if (selectionMode) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            // a decisive swipe either opens or closes; small nudges snap back
                            if (drag < -40f) onRevealChange(true)
                            else if (drag > 40f) onRevealChange(false)
                            drag = 0f
                        }
                    ) { _, amount -> drag += amount }
                }
                .combinedClickable(onLongClick = onLongClick, onClick = onClick)
        ) {
            content()
        }
    }
}

/** A rounded action button revealed by swiping a list row, styled to match miuix's soft shapes. */
@Composable
private fun SwipeActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 58.dp, height = 50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = contentColor, fontSize = 11.sp)
    }
}

@Composable
private fun AboutPage(
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
        }.getOrDefault("-")
    }

    Column(
        Modifier
            .fillMaxSize()
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "HyperGlow",
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "HyperOS 灯效补完计划",
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("版本")
        Card(Modifier.fillMaxWidth()) {
            InfoRow("版本号", version)
            InfoRow("包名", "com.elsure.hyperglow")
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("工作原理")
        Card(Modifier.fillMaxWidth()) {
            InfoRow("LSPosed 模块", "拦截系统隐私灯策略，从源头改写或屏蔽")
            InfoRow("Root 守护进程", "直接写 sysfs 驱动 RGB，压制系统覆盖并在后台持续")
            InfoRow("Binder 通道", "无 Root 时经隐私灯接口点灯，会被相机生命周期覆盖")
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("适用设备")
        Card(Modifier.fillMaxWidth()) {
            InfoRow("机型", "小米平板 8 Pro（piano）")
            InfoRow("系统", "HyperOS")
            InfoRow("依赖", "LSPosed + KernelSU / Magisk")
        }
        Spacer(Modifier.height(28.dp))
    }
}
