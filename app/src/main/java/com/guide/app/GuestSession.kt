package com.guide.app

import android.content.Context

object GuestSession {
    private const val PREFS = "guide_session"
    private const val KEY_GUEST = "guest_mode"

    fun isGuest(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GUEST, false)

    fun start(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_GUEST, true).apply()
        val store = GuideStore(context)
        if (!store.hasProfile()) {
            store.saveProfile("Guest User", "guest-local")
            store.seedDefaultsIfNeeded()
        }
        ReminderScheduler.ensureChannel(context)
        ReminderScheduler.scheduleAll(context, store)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_GUEST, false).apply()
    }
}
