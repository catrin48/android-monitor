package com.hanatsubaki.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relaunch the kiosk on boot (also acts as home app, but this covers OEM quirks). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
