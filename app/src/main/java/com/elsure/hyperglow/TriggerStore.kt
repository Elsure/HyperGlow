package com.elsure.hyperglow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-defined "when X, light up like Y" rules. Unlike notifications (which are momentary EVENTS),
 * these describe a STATE: the light is held for as long as the condition holds and released when it
 * stops. Rules are ranked by [Rule.priority] so overlapping conditions resolve deterministically.
 */
object TriggerStore {

    // Condition kinds. Values are persisted, so do not renumber existing ones.
    const val T_BATTERY_LOW = 0
    const val T_CHARGING = 1
    const val T_BATTERY_FULL = 2
    const val T_BLUETOOTH = 3
    const val T_HEADSET = 4
    const val T_SCREEN_OFF = 5
    const val T_APP_FOREGROUND = 6

    data class Rule(
        val id: Int,
        val type: Int,
        val enabled: Boolean = true,
        /** Threshold percent for battery rules, or a package name for the app rule. */
        val param: String = "",
        val color: Int = 0xFFFF8000.toInt(),
        val effect: Int = NotifStore.EFFECT_SOLID,
        /** Higher wins when several conditions are active at once. */
        val priority: Int = 5,
        /** 0 = hold for as long as the condition lasts; >0 = flash for N seconds. */
        val durationSec: Int = 0,
    )

    private const val PREFS = "hyperglow_trigger"
    private const val K_RULES = "rules"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun typeName(type: Int): String = when (type) {
        T_BATTERY_LOW -> "电量低于阈值"
        T_CHARGING -> "正在充电"
        T_BATTERY_FULL -> "电量充满"
        T_BLUETOOTH -> "蓝牙已连接"
        T_HEADSET -> "耳机已插入"
        T_SCREEN_OFF -> "屏幕关闭"
        T_APP_FOREGROUND -> "指定应用在前台"
        else -> "未知"
    }

    val ALL_TYPES = listOf(
        T_BATTERY_LOW, T_CHARGING, T_BATTERY_FULL,
        T_BLUETOOTH, T_HEADSET, T_SCREEN_OFF, T_APP_FOREGROUND,
    )

    /** Human-readable description of a rule's condition, including its parameter. */
    fun describe(ctx: Context, r: Rule): String = when (r.type) {
        T_BATTERY_LOW -> "电量低于 ${r.param.ifBlank { "20" }}%"
        T_APP_FOREGROUND -> "${NotifStore.appLabel(ctx, r.param)} 在前台"
        else -> typeName(r.type)
    }

    fun rules(ctx: Context): List<Rule> = try {
        val arr = JSONArray(sp(ctx).getString(K_RULES, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Rule(
                id = o.getInt("id"),
                type = o.getInt("type"),
                enabled = o.optBoolean("enabled", true),
                param = o.optString("param", ""),
                color = o.optInt("color", 0xFFFF8000.toInt()),
                effect = o.optInt("effect", NotifStore.EFFECT_SOLID),
                priority = o.optInt("priority", 5),
                durationSec = o.optInt("duration", 0),
            )
        }.sortedByDescending { it.priority }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(ctx: Context, list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("type", r.type).put("enabled", r.enabled)
                    .put("param", r.param).put("color", r.color)
                    .put("effect", r.effect).put("priority", r.priority)
                    .put("duration", r.durationSec)
            )
        }
        sp(ctx).edit().putString(K_RULES, arr.toString()).apply()
    }

    fun upsert(ctx: Context, rule: Rule) {
        write(ctx, rules(ctx).filterNot { it.id == rule.id } + rule)
    }

    fun remove(ctx: Context, id: Int) {
        write(ctx, rules(ctx).filterNot { it.id == id })
    }

    fun nextId(ctx: Context): Int = (rules(ctx).maxOfOrNull { it.id } ?: 0) + 1

    fun anyEnabled(ctx: Context): Boolean = rules(ctx).any { it.enabled }
}
