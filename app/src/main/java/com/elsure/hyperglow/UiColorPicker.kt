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
fun HsvWheelPicker(
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
        // Consume only presses that start INSIDE the wheel; presses on the corners are left to
        // the parent scroll container, so the rest of the dialog stays scrollable.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent().changes.first()
                    if (!down.pressed) continue
                    val r = minOf(size.width, size.height) / 2f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = down.position.x - cx
                    val dy = down.position.y - cy
                    if (dx * dx + dy * dy > r * r) continue // outside the wheel: let the parent scroll handle it
                    down.consume()
                    var lastX = down.position.x
                    var lastY = down.position.y
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.first()
                        if (!ch.pressed) break
                        lastX = ch.position.x
                        lastY = ch.position.y
                        if (ch.pressed != down.pressed || ch.id != down.id) break
                        ch.consume()
                    }
                    var a = Math.toDegrees(kotlin.math.atan2((lastY - cy).toDouble(), (lastX - cx).toDouble())).toFloat()
                    if (a < 0f) a += 360f
                    onHueSatChanged(a, kotlin.math.sqrt((lastX - cx) * (lastX - cx) + (lastY - cy) * (lastY - cy)).coerceAtMost(r) / r)
                }
            }
        })
    }
}

/** Inline colour picker that respects the global pickerStyle (0=sliders, 1=wheel, 2=hex). */

@Composable
fun InlineColorPicker(
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
fun ColorPickerDialog(
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
