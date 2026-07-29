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
fun SpectrumPage(
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
                title = "开关",
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
        SmallTitle("效果调节")
        Card(Modifier.fillMaxWidth()) {
            SliderPreference(
                title = "灵敏度",
                summary = "越高增益越大，亮度越高",
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
            InfoRow("权限说明", "频谱经由守护进程驱动，需授予 Root 权限")
            InfoRow("音源说明", if (s.spectrumPlayback) "部分应用可拒绝被内录，此时为静音" else "其他应用占用麦克风时会被暂时抢占")
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** Three-band live meter: bass / mid / treble, coloured like the LED output. */

@Composable
fun BandMeter(bands: Triple<Float, Float, Float>, active: Boolean) {
    val cs = MiuixTheme.colorScheme
    val labels = listOf("低频", "中频", "高频")
    val colors = listOf(Color(0xFFFF4D4D), Color(0xFF4DD964), Color(0xFF4D9BFF))
    val values = listOf(bands.first, bands.second, bands.third)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().height(120.dp),
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
                                .height(96.dp)
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
                    "未运行 · 打开下方开关启动",
                    color = cs.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
