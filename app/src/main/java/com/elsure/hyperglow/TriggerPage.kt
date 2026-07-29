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
fun TriggerPage(
    vm: HyperGlowViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    val configuration = LocalConfiguration.current
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
            // 内容可能比视口矮，导致 verticalScroll 不激活、到顶/到底无回弹；
            // 保证内容至少一屏高，使滚动容器能到达两端从而触发系统 overscroll 回弹。
            .heightIn(min = configuration.screenHeightDp.dp)
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
fun TriggerRuleDialog(
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
