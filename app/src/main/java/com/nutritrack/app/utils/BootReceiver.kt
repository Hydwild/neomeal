package com.nutritrack.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutritrack.app.utils.ReminderScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
            // Умное напоминание о воде — если включено
            if (PrefsManager(context).smartWaterReminder) {
                SmartWaterScheduler.schedule(context)
            }
        }
    }
}