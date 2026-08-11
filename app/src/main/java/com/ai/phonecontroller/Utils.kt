package com.ai.phonecontroller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import java.net.NetworkInterface
import java.net.URL

object Utils {
    private val gson = Gson()
    private val okHttp = OkHttpClient()
    
    // 🔥 CHANGE THIS TO YOUR LINUX SERVER IP
    private const val LINUX_SERVER_URL = "http://YOUR_LINUX_IP:5678/webhook"

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getPublicIp(): String? {
        return try {
            val url = URL("https://api.ipify.org")
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readText()
        } catch (e: Exception) {
            null
        }
    }

    fun getBatteryLevel(): Int {
        val bm = App.context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun showNotification(title: String, text: String) {
        val context = App.context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ai_channel",
                "AI Controller",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "ai_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    fun openApp(packageName: String) {
        val context = App.context
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun getRunningApps(): String {
        val context = App.context
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86400000, now)
        val list = stats?.map { mapOf("package" to it.packageName, "lastUsed" to it.lastTimeUsed) }
        return list?.toString() ?: "[]"
    }

    fun sendSMS(phone: String, text: String) {
        val sms = SmsManager.getDefault()
        sms.sendTextMessage(phone, null, text, null, null)
    }

    fun makeCall(phone: String) {
        val context = App.context
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun rebootDevice() {
        val context = App.context
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot("AI Controller reboot")
    }

    fun getWifiInfo(): String {
        val context = App.context
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        return mapOf(
            "ssid" to (info.ssid ?: "Unknown"),
            "rssi" to info.rssi,
            "speed" to info.linkSpeed,
            "ip" to info.ipAddress
        ).toString()
    }

    fun sendToLinux(data: Map<String, Any>) {
        try {
            val json = gson.toJson(data)
            val body = RequestBody.create(
                MediaType.parse("application/json"),
                json
            )
            val request = Request.Builder()
                .url(LINUX_SERVER_URL)
                .post(body)
                .build()
            
            okHttp.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Utils", "❌ Failed to send to Linux: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    Log.d("Utils", "✅ Data sent to Linux")
                }
            })
        } catch (e: Exception) {
            Log.e("Utils", "❌ Error sending to Linux: ${e.message}")
        }
    }
}
