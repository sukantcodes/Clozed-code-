package com.clozed.code

import android.app.Application
import android.os.StrictMode
import com.clozed.code.engine.Notifier

/**
 * Application entry point: creates the notification channel and relaxes the
 * main-thread policy. The agent loop streams over HttpURLConnection from a
 * coroutine, and the simple design keeps its callbacks on the main thread, so
 * network and disk on the main thread must be permitted.
 */
class ClozedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().permitAll().build(),
        )
        Notifier.ensureChannel(this)
    }
}
