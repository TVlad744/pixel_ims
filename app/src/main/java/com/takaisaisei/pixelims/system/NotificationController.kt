package com.takaisaisei.pixelims.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.takaisaisei.pixelims.MainActivity
import com.takaisaisei.pixelims.R

class NotificationController(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PAIRING,
                appContext.getString(R.string.notif_channel_pairing_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = appContext.getString(R.string.notif_channel_pairing_desc) }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APPLYING,
                appContext.getString(R.string.notif_channel_applying_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = appContext.getString(R.string.notif_channel_applying_desc) }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                appContext.getString(R.string.notif_channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = appContext.getString(R.string.notif_channel_status_desc) }
        )
    }

    fun showApplying() {
        ensureChannels()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_APPLYING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(appContext.getString(R.string.notif_applying_title))
            .setContentText(appContext.getString(R.string.notif_applying_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(0, 0, true)
            .build()
        notificationManager.notify(NOTIFICATION_STATUS, notification)
    }

    fun showApplyResult(clear: Boolean, imsRegistered: Map<Int, Boolean>) {
        ensureChannels()
        val title = appContext.getString(
            if (clear) R.string.notif_restore_done_title else R.string.notif_apply_done_title
        )
        val text = imsRegistered.toSortedMap().entries.joinToString("  ") { (slot, registered) ->
            appContext.getString(
                if (registered) R.string.notif_apply_ims_registered
                else R.string.notif_apply_ims_unregistered,
                slot + 1,
            )
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_STATUS, notification)
    }

    fun showPairingPrompt() {
        ensureChannels()
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel(appContext.getString(R.string.notif_pairing_reply_label))
            .build()

        val intent = Intent(ACTION_PAIR).setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            appContext.getString(R.string.notif_pairing_action),
            pendingIntent,
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_PAIRING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_pairing_title))
            .setContentText(appContext.getString(R.string.notif_pairing_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action)
            .build()

        notificationManager.notify(NOTIFICATION_PAIRING, notification)
    }

    fun showPairingStatus(text: String) {
        ensureChannels()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_PAIRING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_pairing_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_PAIRING, notification)
    }

    fun cancelPairing() = notificationManager.cancel(NOTIFICATION_PAIRING)

    companion object {
        const val ACTION_PAIR = "com.takaisaisei.pixelims.ACTION_PAIR"
        const val KEY_PAIRING_CODE = "extra_pairing_code"

        private const val CHANNEL_PAIRING = "pairing_channel"
        private const val CHANNEL_APPLYING = "applying_channel"
        private const val CHANNEL_STATUS = "status_channel"
        private const val NOTIFICATION_PAIRING = 202
        private const val NOTIFICATION_STATUS = 203
    }
}
