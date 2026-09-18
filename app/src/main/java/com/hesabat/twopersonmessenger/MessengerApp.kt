package com.hesabat.twopersonmessenger

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MessengerApp : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        getSharedPreferences("security", 0).edit()
            .putLong("background_at", System.currentTimeMillis())
            .putBoolean("was_backgrounded", true)
            .apply()
    }

    override fun onStart(owner: LifecycleOwner) {
        val p = getSharedPreferences("security", 0)
        val delay = p.getLong("auto_clear_ms", 0L)
        val bg = p.getLong("background_at", 0L)
        val edit = p.edit().putBoolean("return_to_documents", p.getBoolean("was_backgrounded", false))

        // Auto-clear OFF means absolutely no automatic cutoff is created.
        if (delay > 0L && bg > 0L && System.currentTimeMillis() - bg >= delay) {
            // Hide only messages that already existed when the app went to background.
            // Messages received while the app was in background remain visible.
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("GMT+04:00")
            edit.putString("cleared_before", f.format(Date(bg)))
        }
        edit.putLong("background_at", 0L).putBoolean("was_backgrounded", false).apply()
    }
}
