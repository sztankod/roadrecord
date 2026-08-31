package hu.roadrecord.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hu.roadrecord.app.ImportantMessageActivity
import hu.roadrecord.app.R

object ImportantMessageNotifier {
    private const val CHANNEL_ID = "important_messages_v2"

    fun alert(context: Context, sender: WatchedSender, source: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Fontos üzenetek", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A figyelt telefonszámoktól és ismerősöktől érkező üzenetek"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 180, 350, 180, 650)
                    setSound(null, null)
                }
            )
        }
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val popupIntent = Intent(context, ImportantMessageActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ImportantMessageActivity.EXTRA_SENDER, sender.label.ifBlank { sender.value })
            .putExtra(ImportantMessageActivity.EXTRA_SOURCE, source)
            .putExtra(ImportantMessageActivity.EXTRA_MESSAGE, message)
            .putExtra(ImportantMessageActivity.EXTRA_NOTIFICATION_ID, notificationId)
        val openPopup = PendingIntent.getActivity(
            context, notificationId, popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.roadrecord_logo)
            .setContentTitle("Fontos üzenet: ${sender.label.ifBlank { sender.value }}")
            .setContentText("$source érkezett egy figyelt feladótól.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$source érkezett egy figyelt feladótól: ${sender.label.ifBlank { sender.value }}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openPopup)
            .setFullScreenIntent(openPopup, true)
            .setOngoing(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        playSelectedSound(context)
        runCatching { context.startActivity(popupIntent) }
    }

    private fun playSelectedSound(context: Context) {
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, MessageAlertStore.soundUri(context))
                val volume = MessageAlertStore.volume(context)
                setVolume(volume, volume)
                setOnCompletionListener { it.release() }
                setOnErrorListener { player, _, _ -> player.release(); true }
                prepare()
                start()
            }
        }
    }
}
