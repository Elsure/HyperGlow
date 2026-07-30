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
fun NotificationPage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    openApp: String? = null,
    onOpenApp: (String?) -> Unit = {},
) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    var editing by remember { mutableStateOf<NotifStore.Rule?>(null) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshNotif() }

    val scroll = Modifier
        .fillMaxSize()
        .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior) else it }
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .heightIn(min = configuration.screenHeightDp.dp)
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
fun NotifRuleDialog(
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
