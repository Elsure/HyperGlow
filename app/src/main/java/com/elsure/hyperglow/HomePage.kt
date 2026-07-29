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
fun HomePage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val s by vm.ui.collectAsState()
    val cs = MiuixTheme.colorScheme
    val configuration = LocalConfiguration.current
    var showColorDialog by remember { mutableStateOf(false) }
    var showMicColorDialog by remember { mutableStateOf(false) }
    ColorPickerDialog(
        show = showColorDialog,
        title = "隐私灯颜色",
        initial = s.privColor,
        pickerStyle = s.pickerStyle,
        onColorChanged = { vm.setPrivacyColor(it) },
        onDismiss = { showColorDialog = false }
    )
    ColorPickerDialog(
        show = showMicColorDialog,
        title = "麦克风隐私灯颜色",
        initial = s.micColor,
        pickerStyle = s.pickerStyle,
        onColorChanged = { vm.setMicColor(it) },
        onDismiss = { showMicColorDialog = false }
    )
    Column(
        Modifier
            .fillMaxSize()
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            // 内容可能比视口矮，导致 verticalScroll 不激活、到顶/到底无回弹；
            // 保证内容至少一屏高，使滚动容器能到达两端从而触发系统 overscroll 回弹。
            .heightIn(min = configuration.screenHeightDp.dp)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ---- hero + status, side by side ----
        val working = s.takeover
        val accent = if (working) Color(0xFFFF5522) else cs.primary
        val heroTitle = when {
            working -> "Dokidoki"
            s.rootStatus.startsWith("已连接") -> "Root 已就绪"
            else -> "Kirakira"
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
                    InfoRow(
                        "隐私灯",
                        when { !s.privEnabled -> "未接管"
                               s.privMode == 1 -> "自定义"
                               s.privMode == 2 -> "隐藏"
                               else -> "原色" }
                    )
                    InfoRow("Root", s.rootStatus)
                    InfoRow("连接后端", s.status)
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
                summary = "白光2秒正弦呼吸灯",
                onClick = { vm.testLight() }
            )
            ArrowPreference(title = "关闭指示灯", onClick = { vm.turnOff() })
        }
        Spacer(Modifier.height(28.dp))
    }
}
