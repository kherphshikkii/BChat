package com.kherphy.bchat

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@SuppressLint("MissingPermission")
object Notifier {

    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_REQUESTS = "requests"
    const val ACTION_ACCEPT = "com.kherphy.bchat.ACCEPT"
    const val ACTION_DECLINE = "com.kherphy.bchat.DECLINE"

    private const val REQUEST_ID = 2
    private const val MESSAGE_ID_BASE = 100
    private const val REQUEST_TIMEOUT_MS = 30_000L

    private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New messages from nearby phones"
            enableVibration(true)
        }
        val requests = NotificationChannel(
            CHANNEL_REQUESTS,
            "Connection requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Requests to connect, when Ask before connecting is on"
        }
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(messages, requests))
    }

    private fun messageId(address: String): Int =
        MESSAGE_ID_BASE + (address.hashCode() and 0x00FFFFFF)

    fun showMessage(address: String, name: String, preview: String, count: Int, alert: Boolean) {
        val context = appContext ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val id = messageId(address)
        val text = when {
            preview.isEmpty() -> if (count > 1) "$count new messages" else "New message"
            count > 1 -> "$count new messages · $preview"
            else -> preview
        }

        val open = Intent(context, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_OPEN_ADDRESS, address)
            .putExtra(HomeActivity.EXTRA_OPEN_NAME, name)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        val pending = PendingIntent.getActivity(
            context,
            id,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle("Bluetooth Chat")
            .setContentText("New message")
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setNumber(count)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOnlyAlertOnce(!alert)
            .build()

        manager.notify(id, notification)
    }

    fun cancelMessages(address: String) {
        val context = appContext ?: return
        NotificationManagerCompat.from(context).cancel(messageId(address))
    }

    fun cancelAllMessages() {
        val context = appContext ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_MESSAGES }
            .forEach { manager.cancel(it.id) }
    }

    fun showRequest(name: String) {
        val context = appContext ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = Intent(context, HomeActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        val openPending = PendingIntent.getActivity(
            context,
            REQUEST_ID,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle("Connection request")
            .setContentText("$name wants to chat with you.")
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setTimeoutAfter(REQUEST_TIMEOUT_MS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Accept", servicePending(context, ACTION_ACCEPT, 10))
            .addAction(0, "Decline", servicePending(context, ACTION_DECLINE, 11))
            .build()

        manager.notify(REQUEST_ID, notification)
    }

    fun cancelRequest() {
        val context = appContext ?: return
        NotificationManagerCompat.from(context).cancel(REQUEST_ID)
    }

    private fun servicePending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ChatService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}