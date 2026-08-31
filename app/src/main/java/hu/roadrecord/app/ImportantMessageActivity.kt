package hu.roadrecord.app

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.os.Bundle
import android.view.WindowManager

class ImportantMessageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showMessage(intent.getStringExtra(EXTRA_SENDER).orEmpty(), intent.getStringExtra(EXTRA_SOURCE).orEmpty(), intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showMessage(intent.getStringExtra(EXTRA_SENDER).orEmpty(), intent.getStringExtra(EXTRA_SOURCE).orEmpty(), intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
    }

    private fun showMessage(sender: String, source: String, message: String) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        AlertDialog.Builder(this)
            .setIcon(R.drawable.roadrecord_logo)
            .setTitle("Fontos üzenet – $sender")
            .setMessage(buildString {
                append(source)
                if (message.isNotBlank()) append("\n\n").append(message)
            })
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                if (notificationId >= 0) getSystemService(NotificationManager::class.java).cancel(notificationId)
                finishAndRemoveTask()
            }
            .show()
    }

    override fun onBackPressed() = Unit

    companion object {
        const val EXTRA_SENDER = "sender"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
