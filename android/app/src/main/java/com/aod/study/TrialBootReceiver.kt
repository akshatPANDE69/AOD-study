package com.aod.study

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Invalidates an in-flight measurement after reboot; elapsed-realtime alarms cannot survive reboot. */
class TrialBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(StudyTrialStore.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(StudyTrialStore.ACTIVE, false)) return

        prefs.edit()
            .putBoolean(StudyTrialStore.ACTIVE, false)
            .putString(
                StudyTrialStore.LAST_RESULT,
                "Trial invalidated because the phone restarted. Repeat this condition; no measurement row was saved."
            )
            .remove(StudyTrialStore.START_SNAPSHOT)
            .remove(StudyTrialStore.TARGET_ELAPSED_MS)
            .remove(StudyTrialStore.PENDING_START_TARGET_MS)
            .remove(StudyTrialStore.SESSION_START_ELAPSED_MS)
            .putLong(StudyTrialStore.LAST_SESSION_DURATION_MS, 0L)
            .remove("metadata")
            .apply()
    }
}
