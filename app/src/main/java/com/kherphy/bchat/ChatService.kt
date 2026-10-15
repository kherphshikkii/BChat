package com.kherphy.bchat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ChatService : Service() {

    private var quitting = false

    private val listener = object : ChatConnection.Listener {
        override fun onStateChanged(state: ChatConnection.State) {
            refreshNotification()
        }

        override fun onRemoteNameChanged(name: String) {
            refreshNotification()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ChatConnection.refreshListening()
            refreshNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ChatConnection.init(this)
        createChannel()
        startInForeground()
        ChatConnection.addListener(listener)
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        ChatConnection.refreshListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> ChatConnection.disconnect()
            ACTION_QUIT -> quit()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ChatConnection.removeListener(listener)
        unregisterReceiver(bluetoothReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun quit() {
        quitting = true
        ChatConnection.disconnect()
        ChatConnection.stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Connection status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while Bluetooth Chat is ready to chat"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        if (quitting) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = ChatConnection.state
        val name = ChatConnection.remoteName.ifBlank { "device" }
        val bluetoothOn = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

        val text = when {
            state == ChatConnection.State.CONNECTED -> "Connected to $name"
            state == ChatConnection.State.CONNECTING -> "Connecting to $name…"
            !bluetoothOn -> "Bluetooth is off"
            state == ChatConnection.State.LISTENING -> "Ready for connections"
            else -> "Not connected"
        }

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, HomeActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setColor(ContextCompat.getColor(this, R.color.primary))
            .setContentTitle("Bluetooth Chat")
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state == ChatConnection.State.CONNECTED) {
            builder.addAction(0, "Disconnect", servicePending(ACTION_DISCONNECT, 1))
        }
        builder.addAction(0, "Quit", servicePending(ACTION_QUIT, 2))

        return builder.build()
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ChatService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "com.kherphy.bchat.DISCONNECT"
        private const val ACTION_QUIT = "com.kherphy.bchat.QUIT"

        fun start(context: Context) {
            if (!BtPermissions.hasAll(context)) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ChatService::class.java)
                )
            } catch (e: Exception) {
            }
        }
    }
}