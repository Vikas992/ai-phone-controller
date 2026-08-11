package com.ai.phonecontroller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import okhttp3.*
import java.util.concurrent.TimeUnit

class HttpServerService(private val context: Context) : NanoHTTPD(8080) {

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var audioRecorder: AudioRecord? = null
    private var isRecording = false
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        showNotification("AI Controller", "Server running on port 8080")
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val body = getBody(session)
            val cmd = gson.fromJson(body, Command::class.java)
            Log.d("Server", "📩 Command: ${cmd.action}")

            when (cmd.action) {
                "list_files" -> {
                    val path = cmd.path ?: "/sdcard"
                    val files = FileManager.listFiles(path)
                    success(files)
                }
                "read_file" -> {
                    val content = FileManager.readFile(cmd.path ?: "")
                    success(content)
                }
                "delete_file" -> {
                    FileManager.deleteFile(cmd.path ?: "")
                    success("File deleted")
                }
                "take_photo" -> {
                    val photo = FileManager.takePhoto()
                    success(photo)
                }
                "start_video" -> {
                    FileManager.startVideoRecording()
                    success("Video recording started")
                }
                "stop_video" -> {
                    val video = FileManager.stopVideoRecording()
                    success(video)
                }
                "answer_call" -> {
                    CallController.answerCall()
                    success("Call answered")
                }
                "reject_call" -> {
                    CallController.rejectCall()
                    success("Call rejected")
                }
                "make_call" -> {
                    Utils.makeCall(cmd.phone ?: "")
                    success("Calling ${cmd.phone}")
                }
                "start_audio_stream" -> {
                    cmd.ws_url?.let { startAudioStream(it) }
                        ?: error("WebSocket URL missing")
                    success("Audio streaming started")
                }
                "stop_audio_stream" -> {
                    stopAudioStream()
                    success("Audio streaming stopped")
                }
                "show_notification" -> {
                    Utils.showNotification(cmd.title ?: "AI", cmd.text ?: "Hello")
                    success("Notification shown")
                }
                "get_battery" -> {
                    val battery = Utils.getBatteryLevel()
                    success(battery.toString())
                }
                "open_app" -> {
                    Utils.openApp(cmd.package_name ?: "")
                    success("App opened")
                }
                "send_sms" -> {
                    Utils.sendSMS(cmd.phone ?: "", cmd.text ?: "")
                    success("SMS sent")
                }
                "get_ip" -> {
                    val ip = mapOf(
                        "local" to Utils.getLocalIpAddress(),
                        "public" to Utils.getPublicIp()
                    )
                    success(gson.toJson(ip))
                }
                "reboot" -> {
                    Utils.rebootDevice()
                    success("Rebooting...")
                }
                else -> error("Unknown command: ${cmd.action}")
            }
        } catch (e: Exception) {
            Log.e("Server", "❌ Error: ${e.message}")
            error(e.message ?: "Unknown error")
        }
    }

    private fun startAudioStream(wsUrl: String) {
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("WS", "✅ Connected")
                startRecording()
            }
            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("WS", "📨 AI: $text")
                Utils.showNotification("AI Voice", text)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("WS", "⏹️ Closed")
                stopRecording()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("WS", "❌ Failed: ${t.message}")
                stopRecording()
            }
        })
    }

    private fun stopAudioStream() {
        webSocket?.close(1000, "Stopped")
        webSocket = null
        stopRecording()
    }

    private fun startRecording() {
        if (isRecording) return
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecorder?.startRecording()
        isRecording = true
        Log.d("Audio", "🎙️ Recording started")

        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && webSocket != null) {
                    webSocket?.send(buffer.copyOf(read))
                }
            }
        }.start()
    }

    private fun stopRecording() {
        isRecording = false
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
        Log.d("Audio", "⏹️ Recording stopped")
    }

    private fun showNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "server_channel",
                "Server Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "server_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(999, notification)
    }

    private fun getBody(session: IHTTPSession): String {
        val size = session.headers["content-length"]?.toInt() ?: 0
        val buffer = ByteArray(size)
        session.inputStream.read(buffer)
        return String(buffer)
    }

    private fun success(msg: Any): Response {
        val json = gson.toJson(mapOf("status" to "success", "data" to msg))
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun error(msg: String): Response {
        val json = gson.toJson(mapOf("status" to "error", "message" to msg))
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", json)
    }

    data class Command(
        val action: String,
        val path: String? = null,
        val phone: String? = null,
        val text: String? = null,
        val title: String? = null,
        val package_name: String? = null,
        val ws_url: String? = null
    )
}
