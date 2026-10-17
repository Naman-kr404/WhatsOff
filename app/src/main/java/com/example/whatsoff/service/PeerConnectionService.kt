package com.example.whatsoff.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.whatsoff.MainActivity

/**
 * Foreground Service guaranteeing socket durability when WhatsOff is minimized or backgrounded.
 * Complies with Android 14+ (API 34-36) FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE requirements.
 */
class PeerConnectionService : Service {

    constructor() : super()

    companion object {
        const val CHANNEL_ID = "whatsoff_connection_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_HOSTING = "com.example.whatsoff.action.START_HOSTING"
        const val ACTION_START_CONNECTED = "com.example.whatsoff.action.START_CONNECTED"
        const val ACTION_STOP = "com.example.whatsoff.action.STOP"

        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PEER_NAME = "extra_peer_name"

        fun startHosting(context: Context, port: Int) {
            val intent = Intent(context, PeerConnectionService::class.java).apply {
                action = ACTION_START_HOSTING
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startConnected(context: Context, peerName: String) {
            val intent = Intent(context, PeerConnectionService::class.java).apply {
                action = ACTION_START_CONNECTED
                putExtra(EXTRA_PEER_NAME, peerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PeerConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_HOSTING -> {
                val port = intent.getIntExtra(EXTRA_PORT, 5000)
                val notification = buildNotification(
                    title = "WhatsOff - Hosting Chat",
                    content = "Listening for peers on port $port"
                )
                startForegroundWithCompatibleType(notification)
            }
            ACTION_START_CONNECTED -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Peer"
                val notification = buildNotification(
                    title = "WhatsOff - Active Chat",
                    content = "🔒 E2E Encrypted session with $peerName"
                )
                startForegroundWithCompatibleType(notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithCompatibleType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WhatsOff Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps WhatsOff peer connections active in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
