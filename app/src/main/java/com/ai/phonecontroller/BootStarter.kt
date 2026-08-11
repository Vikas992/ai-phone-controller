package com.ai.phonecontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootStarter", "📱 Phone booted, starting server...")
            
            val serviceIntent = Intent(context, HttpServerService::class.java)
            context.startForegroundService(serviceIntent)
            
            Thread {
                val ip = Utils.getLocalIpAddress()
                Utils.sendToLinux(mapOf(
                    "event" to "phone_booted",
                    "local_ip" to (ip ?: "unknown"),
                    "timestamp" to System.currentTimeMillis()
                ))
            }.start()
            
            Utils.showNotification("🤖 AI Controller", "Phone is online and controlled by AI")
        }
    }
}
