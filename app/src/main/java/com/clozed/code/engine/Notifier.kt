package com.clozed.code.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** Posts the foreground-service notification and agent alerts. */
object Notifier {
    const val CHANNEL = "agent_channel"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Clozed Code is working", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    fun running(ctx: Context, title: String) =
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun alert(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build())
    }
}
