package com.clozed.code.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Keeps the agent alive while it works in the background and hosts the
 * ongoing notification. The actual agent loop lives in [LocalBackend].
 */
class AgentService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannel(this)
        startForeground(NOTIFICATION_ID, Notifier.running(this, "Clozed Code is working"))
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.clozed.code.START"
        const val ACTION_STOP = "com.clozed.code.STOP"
    }
}
