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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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
                    summary = "将强制停止 com.miui.securitycenter，使 LSPosed 重新注入 Hook。",
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
                            text = "确定",
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
