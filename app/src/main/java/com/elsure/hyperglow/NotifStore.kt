package com.elsure.hyperglow

import android.content.Context
import android.content.pm.PackageManager
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Rules that map a notification to a light effect, plus the catalogue of (app, channel) pairs the
 * listener has actually seen. Channels cannot be enumerated up front for other apps, so the UI is
 * populated as notifications arrive — the user configures what they have actually received.
 *
 * Matching order, most specific first: exact (pkg, channel) -> that app's default -> global default.
 */
object NotifStore {

    const val ANY_APP = "*"
    const val ANY_CHANNEL = ""

    const val EFFECT_SOLID = 0
    const val EFFECT_BREATH = 1
    const val EFFECT_BLINK = 2

    data class Rule(
        val pkg: String,
        val channelId: String = ANY_CHANNEL,
        val enabled: Boolean = true,
        val color: Int = 0xFF00A0FF.toInt(),
        val effect: Int = EFFECT_BREATH,
        val durationSec: Int = 10,
    ) {
        val key: String get() = "$pkg|$channelId"
    }

    data class Seen(val pkg: String, val channelId: String)

    data class ChannelInfo(val id: String, val name: String)

    private const val POLICY_FILE = "/data/system/notification_policy.xml"

    /**
     * Every app's notification channels, read from the system's persisted policy file via root.
     *
     * The public API for this (NotificationListenerService.getNotificationChannels) is restricted
     * to companion-device listeners and the notification assistant, so a plain listener cannot use
     * it. The system persists all channels here, so with root we can enumerate them directly —
     * apps do not have to have sent a notification yet.
     */
    data class ScanResult(
        val channels: Map<String, List<ChannelInfo>>,
        val message: String,
    )

    /**
     * Every app's notification channels, read from the system's persisted policy file via root.
     *
     * The public API for this (NotificationListenerService.getNotificationChannels) is restricted
     * to companion-device listeners and the notification assistant, so a plain listener cannot use
     * it. The system persists all channels here, so with root we can enumerate them directly —
     * apps do not have to have sent a notification yet.
     *
     * Each failure stage reports separately: "no channels" has several very different causes and a
     * single generic message makes them impossible to tell apart.
     */
    fun loadAllChannels(): ScanResult {
        if (!RootSession.isAvailable(force = true)) {
            return ScanResult(
                emptyMap(),
                "未获得 Root 授权。改包名后是全新应用，需要在 KernelSU 中重新为 HyperGlow 授权。"
            )
        }
        val exists = RootSession.exec("[ -f $POLICY_FILE ] && echo yes").second.contains("yes")
        if (!exists) {
            val alt = RootSession.exec(
                "ls /data/system/notification*.xml /data/system/users/0/notification*.xml 2>/dev/null"
            ).second
            return ScanResult(
                emptyMap(),
                "未找到 $POLICY_FILE" + if (alt.isNotBlank()) "；但发现：$alt" else "（该系统可能存放在别处）"
            )
        }
        // Android 12+ stores system XML as ABX (Android Binary XML), so `cat` yields binary that a
        // text parser cannot read. The platform ships abx2xml to convert it back; fall back to a
        // plain read for older/plain-text files.
        var xml = RootSession.execLarge("abx2xml $POLICY_FILE - 2>/dev/null")
        var via = "abx2xml"
        if (!looksLikeXml(xml)) {
            xml = RootSession.execLarge("cat $POLICY_FILE")
            via = "cat"
        }
        if (xml.isBlank()) {
            return ScanResult(emptyMap(), "文件存在但读取为空，可能被 SELinux 拦截")
        }
        if (!looksLikeXml(xml)) {
            return ScanResult(
                emptyMap(),
                "读到 ${xml.length} 字节但不是文本 XML（应为 ABX 二进制），且 abx2xml 不可用"
            )
        }
        val map = parseChannels(xml)
        return if (map.isEmpty()) {
            ScanResult(
                emptyMap(),
                "已通过 $via 读取 ${xml.length} 字节，但未解析出 channel 节点（格式可能不同）"
            )
        } else {
            val total = map.values.sumOf { it.size }
            ScanResult(map, "已找到 ${map.size} 个应用、$total 个通知类别")
        }
    }

    private fun looksLikeXml(text: String): Boolean =
        text.contains("<package") || text.contains("<notification-policy") || text.contains("<channel")

    /** Parse `<package name=..><channel id=.. name=../></package>` into a per-package map. */
    fun parseChannels(xml: String): Map<String, List<ChannelInfo>> {
        val out = LinkedHashMap<String, MutableList<ChannelInfo>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var pkg: String? = null
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "package" -> pkg = parser.getAttributeValue(null, "name")
                        "channel" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val owner = pkg
                            if (id != null && owner != null) {
                                val name = parser.getAttributeValue(null, "name") ?: id
                                out.getOrPut(owner) { mutableListOf() }
                                    .add(ChannelInfo(id, name))
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "package") pkg = null
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            // a malformed/partial dump simply yields whatever parsed so far
        }
        return out
    }

    private const val PREFS = "hyperglow_notif"
    private const val K_RULES = "rules"
    private const val K_SEEN = "seen"
    private const val MAX_SEEN = 200

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- rules ----
    fun rules(ctx: Context): List<Rule> = try {
        val arr = JSONArray(sp(ctx).getString(K_RULES, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Rule(
                pkg = o.getString("pkg"),
                channelId = o.optString("channel", ANY_CHANNEL),
                enabled = o.optBoolean("enabled", true),
                color = o.optInt("color", 0xFF00A0FF.toInt()),
                effect = o.optInt("effect", EFFECT_BREATH),
                durationSec = o.optInt("duration", 10),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeRules(ctx: Context, list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("pkg", r.pkg).put("channel", r.channelId)
                    .put("enabled", r.enabled).put("color", r.color)
                    .put("effect", r.effect).put("duration", r.durationSec)
            )
        }
        sp(ctx).edit().putString(K_RULES, arr.toString()).apply()
    }

    fun upsert(ctx: Context, rule: Rule) {
        val list = rules(ctx).filterNot { it.key == rule.key }
        writeRules(ctx, list + rule)
    }

    fun remove(ctx: Context, pkg: String, channelId: String) {
        writeRules(ctx, rules(ctx).filterNot { it.pkg == pkg && it.channelId == channelId })
    }

    /** Most specific matching rule, or null when nothing applies. */
    fun match(ctx: Context, pkg: String, channelId: String): Rule? {
        val list = rules(ctx)
        return list.firstOrNull { it.pkg == pkg && it.channelId == channelId }
            ?: list.firstOrNull { it.pkg == pkg && it.channelId == ANY_CHANNEL }
            ?: list.firstOrNull { it.pkg == ANY_APP }
    }

    // ---- observed (app, channel) catalogue ----
    fun seen(ctx: Context): List<Seen> = try {
        val arr = JSONArray(sp(ctx).getString(K_SEEN, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Seen(o.getString("pkg"), o.optString("channel", ANY_CHANNEL))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun recordSeen(ctx: Context, pkg: String, channelId: String) {
        val cur = seen(ctx)
        if (cur.any { it.pkg == pkg && it.channelId == channelId }) return
        val next = (cur + Seen(pkg, channelId)).takeLast(MAX_SEEN)
        val arr = JSONArray()
        next.forEach { arr.put(JSONObject().put("pkg", it.pkg).put("channel", it.channelId)) }
        sp(ctx).edit().putString(K_SEEN, arr.toString()).apply()
    }

    fun clearSeen(ctx: Context) {
        sp(ctx).edit().remove(K_SEEN).apply()
    }

    /** Human-readable app name, falling back to the package name for uninstalled apps. */
    fun appLabel(ctx: Context, pkg: String): String {
        if (pkg == ANY_APP) return "所有应用"
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun effectName(effect: Int): String = when (effect) {
        EFFECT_SOLID -> "常亮"
        EFFECT_BLINK -> "闪烁"
        else -> "呼吸"
    }
}
