package com.flowdictation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/**
 * A foreground service of type "microphone". Android only lets an app record in the
 * background while one of these is running, and it must be started while the app is
 * visible - which is why the app has a Start button and starts it when you open it.
 */
class DictationService : Service() {
    companion object {
        @Volatile
        var isRunning = false
            private set

        private const val CHANNEL = "dictation_engine"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.flowdictation.STOP"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DictationService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DictationService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).userStopped = true
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            promoteToForeground()
            isRunning = true
            DictationAccessibilityService.instance?.refresh()
        } catch (e: Exception) {
            // Missing mic permission or not allowed to start right now.
            isRunning = false
            stopSelf()
        }
        // Not sticky: if Android kills us, restarting from the background would not be allowed to use the mic.
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Dictation engine", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, DictationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Stop", stopIntent
        ).build()

        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Flow Dictation is ready")
            .setContentText("Tap the floating mic to dictate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(stopAction)
            .build()

        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        isRunning = false
        Dictation.reset()
        DictationAccessibilityService.instance?.refresh()
        super.onDestroy()
    }
}
