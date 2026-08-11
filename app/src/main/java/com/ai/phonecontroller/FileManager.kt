package com.ai.phonecontroller

import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileManager {
    private var mediaRecorder: MediaRecorder? = null
    private var videoPath: String = ""
    private var camera: Camera? = null

    fun listFiles(path: String): String {
        val file = File(path)
        if (!file.exists()) return "[]"

        val list = file.listFiles() ?: return "[]"
        val json = list.map {
            mapOf(
                "name" to it.name,
                "isDir" to it.isDirectory,
                "size" to it.length(),
                "path" to it.absolutePath,
                "modified" to Date(it.lastModified()).toString()
            )
        }
        return json.toString()
    }

    fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists()) return "File not found"
        if (file.length() > 1024 * 1024) return "File too large (>1MB)"
        return file.readText()
    }

    fun deleteFile(path: String): Boolean {
        val file = File(path)
        return file.delete()
    }

    fun takePhoto(): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "AI_photo_$timeStamp.jpg")
        
        try {
            camera = Camera.open()
            camera?.takePicture(null, null, Camera.PictureCallback { data, _ ->
                data?.let {
                    file.writeBytes(it)
                    Log.d("Camera", "📸 Photo saved: ${file.absolutePath}")
                }
            })
        } catch (e: Exception) {
            Log.e("Camera", "❌ Error taking photo: ${e.message}")
        } finally {
            camera?.release()
            camera = null
        }
        return file.absolutePath
    }

    fun startVideoRecording() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        videoPath = File(dir, "AI_video_$timeStamp.mp4").absolutePath

        try {
            camera = Camera.open()
            mediaRecorder = MediaRecorder().apply {
                setCamera(camera)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(640, 480)
                setVideoFrameRate(30)
                setOutputFile(videoPath)
                setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))
                prepare()
                start()
                Log.d("Camera", "🎥 Video recording started: $videoPath")
            }
        } catch (e: Exception) {
            Log.e("Camera", "❌ Error starting video: ${e.message}")
        }
    }

    fun stopVideoRecording(): String {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            camera?.release()
            camera = null
            Log.d("Camera", "⏹️ Video saved: $videoPath")
        } catch (e: Exception) {
            Log.e("Camera", "❌ Error stopping video: ${e.message}")
        }
        return videoPath
    }
}
