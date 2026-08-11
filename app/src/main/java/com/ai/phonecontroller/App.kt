package com.ai.phonecontroller

import android.app.Application
import android.content.Context
import android.util.Log

class App : Application() {
    companion object {
        lateinit var context: Context
        private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        
        Log.d("App", "🚀 App installed, starting server...")
        startServer()
        sendIpToLinux()
    }

    private fun startServer() {
        try {
            val server = HttpServerService(context)
            server.start()
            Log.d("App", "✅ Server started on port 8080")
        } catch (e: Exception) {
            Log.e("App", "❌ Server error: ${e.message}")
        }
    }

    private fun sendIpToLinux() {
        Thread {
            try {
                val ip = Utils.getLocalIpAddress()
                val publicIp = Utils.getPublicIp()
                
                Utils.sendToLinux(
                    mapOf(
                        "event" to "phone_online",
                        "local_ip" to (ip ?: "unknown"),
                        "public_ip" to (publicIp ?: "unknown"),
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                Log.d("App", "📡 IP sent to Linux: $ip")
            } catch (e: Exception) {
                Log.e("App", "❌ Failed to send IP: ${e.message}")
            }
        }.start()
    }
}
