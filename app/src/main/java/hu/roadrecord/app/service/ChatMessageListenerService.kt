package hu.roadrecord.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ChatMessageListenerService : NotificationListenerService() {
    private val handled = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val knownChatPackage = listOf("whatsapp", "facebook.orca", "viber", "telegram", "signal", "discord", "messaging", "messages").any { sbn.packageName.contains(it, ignoreCase = true) }
        if (sbn.notification.category != Notification.CATEGORY_MESSAGE && !knownChatPackage) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val key = "${sbn.packageName}:${sbn.id}:${sbn.postTime}"
        if (handled.containsKey(key)) return
        handled[key] = sbn.postTime
        while (handled.size > 100) handled.remove(handled.keys.first())
        MessageAlertStore.matchingChat(this, title, text)?.let { ImportantMessageNotifier.alert(this, it, "Csevegőüzenet", text) }
    }
}
