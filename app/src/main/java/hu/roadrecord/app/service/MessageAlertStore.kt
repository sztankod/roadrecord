package hu.roadrecord.app.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class WatchedSenderType { SMS, CHAT }

data class WatchedSender(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val value: String,
    val type: WatchedSenderType,
    val enabled: Boolean = true
)

object MessageAlertStore {
    private const val PREFS = "message_alerts"
    private const val KEY_SENDERS = "watched_senders"
    private const val KEY_SOUND = "alert_sound"
    private const val KEY_VOLUME = "alert_volume"

    fun load(context: Context): List<WatchedSender> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SENDERS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    WatchedSender(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        label = item.optString("label"),
                        value = item.optString("value"),
                        type = runCatching { WatchedSenderType.valueOf(item.optString("type")) }.getOrDefault(WatchedSenderType.SMS),
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, senders: List<WatchedSender>) {
        val array = JSONArray()
        senders.forEach { sender ->
            array.put(JSONObject().apply {
                put("id", sender.id)
                put("label", sender.label)
                put("value", sender.value)
                put("type", sender.type.name)
                put("enabled", sender.enabled)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SENDERS, array.toString()).apply()
    }

    fun soundUri(context: Context): Uri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_SOUND, null)?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    fun volume(context: Context): Float = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)

    fun saveAlertOptions(context: Context, sound: Uri, volume: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SOUND, sound.toString())
            .putFloat(KEY_VOLUME, volume.coerceIn(0f, 1f))
            .apply()
    }

    fun matchingSms(context: Context, address: String): WatchedSender? {
        val incoming = normalizePhone(address)
        return load(context).firstOrNull { sender ->
            sender.enabled && sender.type == WatchedSenderType.SMS && phoneMatches(normalizePhone(sender.value), incoming)
        }
    }

    fun matchingChat(context: Context, title: String, text: String): WatchedSender? =
        load(context).firstOrNull { sender ->
            if (!sender.enabled || sender.type != WatchedSenderType.CHAT) return@firstOrNull false
            val name = sender.value.trim()
            name.isNotBlank() && (title.contains(name, ignoreCase = true) || text.startsWith("$name:", ignoreCase = true))
        }

    private fun normalizePhone(value: String) = value.filter(Char::isDigit)
    private fun phoneMatches(configured: String, incoming: String): Boolean {
        if (configured.isBlank() || incoming.isBlank()) return false
        val length = minOf(9, configured.length, incoming.length)
        return length >= 7 && configured.takeLast(length) == incoming.takeLast(length)
    }
}
