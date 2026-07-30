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
fun SettingsPage(
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
        AboutPage(contentPadding, scrollBehavior, onOpenDetail)
        return
    }
    if (detail == "references") {
        ReferencesPage(contentPadding, scrollBehavior)
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
        SmallTitle("偏好")
        Card(Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = "取色方式",
                summary = "选择颜色时使用的交互",
                items = listOf("滑条（色相 / 饱和度）", "调色盘", "键入色值"),
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
