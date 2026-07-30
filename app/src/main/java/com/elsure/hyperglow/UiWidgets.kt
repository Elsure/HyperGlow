package com.elsure.hyperglow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeActionRow(
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
fun SwipeActionChip(
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
fun AboutPage(
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    onOpenDetail: (String?) -> Unit = {},
) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
        }.getOrDefault("-")
    }
    fun openUrl(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
        SmallTitle("构建信息")
        Card(Modifier.fillMaxWidth()) {
            InfoRow("作者", "Elsure")
            InfoRow("版本号", version)
            InfoRow("包名", "com.elsure.hyperglow")
            ArrowPreference(
                title = "项目地址",
                summary = "github.com/Elsure/HyperGlow",
                onClick = { openUrl("https://github.com/Elsure/HyperGlow") }
            )
            ArrowPreference(
                title = "开源引用",
                summary = "查看本项目使用的所有开源库",
                onClick = { onOpenDetail("references") }
            )
        }

        Spacer(Modifier.height(10.dp))
        SmallTitle("工作原理")
        Card(Modifier.fillMaxWidth()) {
            InfoRow("LSPosed 模块", "拦截系统隐私灯策略，从源头改写或屏蔽")
            InfoRow("Root 守护进程", "直接写 sysfs 驱动 RGB，压制系统覆盖并在后台持续")
            InfoRow("Binder 通道", "无 Root 时经隐私灯接口点灯，会被相机生命周期覆盖")
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** 开源引用子页面：列出所有引用的项目、作者与链接。 */
@Composable
fun ReferencesPage(
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val ctx = LocalContext.current
    fun openUrl(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    data class Ref(val name: String, val author: String, val desc: String, val url: String)
    val refs = remember {
        listOf(
            Ref("LSPosed", "LSPosed Developers", "Xposed 框架，用于 Hook 系统隐私灯策略", "https://github.com/LSPosed/LSPosed"),
            Ref("KernelSU", "tiann", "内核级 Root 方案", "https://github.com/tiann/KernelSU"),
            Ref("Magisk", "topjohnwu", "系统级 Root 框架", "https://github.com/topjohnwu/Magisk"),
            Ref("MiuixKMP", "compose-miuix-ui", "MIUI 风格 Compose Multiplatform UI 库", "https://github.com/compose-miuix-ui/miuix"),
            Ref("Jetpack Compose", "Google / JetBrains", "Android 声明式 UI 框架", "https://developer.android.com/jetpack/compose"),
            Ref("Kotlin", "JetBrains", "编程语言", "https://github.com/JetBrains/kotlin"),
            Ref("XposedBridge API", "rovo89", "Xposed 桥接接口（编译期引用）", "https://github.com/rovo89/XposedBridge"),
        )
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
        SmallTitle("开源引用")
        Card(Modifier.fillMaxWidth()) {
            refs.forEach { ref ->
                ArrowPreference(
                    title = ref.name,
                    summary = "${ref.author} · ${ref.desc}",
                    onClick = { openUrl(ref.url) }
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
