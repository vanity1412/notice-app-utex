package com.utex.deadline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.schedulePeriodicSync(context)
            ReminderScheduler.scheduleDailySummary(context)
            ReminderScheduler.scheduleAll(
                context,
                EventStore.loadEvents(context).filterNot { EventStore.isDone(context, it.id) }
            )
            ReminderScheduler.scheduleImmediateSync(context)
        }
    }
}
