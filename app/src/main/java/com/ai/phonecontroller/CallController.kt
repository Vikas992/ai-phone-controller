package com.ai.phonecontroller

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class CallController : InCallService() {

    companion object {
        private var currentCall: Call? = null
        private var callerNumber: String? = null

        fun answerCall() {
            currentCall?.answer(Call.Details.CAPABILITY_SUPPORTS_VT_AUDIO)
            Log.d("CallController", "✅ Call answered")
            Utils.sendToLinux(mapOf(
                "event" to "call_answered",
                "caller" to (callerNumber ?: "Unknown")
            ))
        }

        fun rejectCall() {
            currentCall?.reject(false, null)
            Log.d("CallController", "❌ Call rejected")
            Utils.sendToLinux(mapOf(
                "event" to "call_rejected",
                "caller" to (callerNumber ?: "Unknown")
            ))
        }

        fun getCallerNumber(): String? = callerNumber

        fun muteCall() {
            currentCall?.mute()
            Log.d("CallController", "🔇 Call muted")
        }

        fun disconnectCall() {
            currentCall?.disconnect()
            Log.d("CallController", "📞 Call disconnected")
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        callerNumber = call.details.handle?.schemeSpecificPart
        Log.d("CallController", "📞 Incoming call from: $callerNumber")

        Utils.sendToLinux(mapOf(
            "event" to "incoming_call",
            "caller" to (callerNumber ?: "Unknown"),
            "timestamp" to System.currentTimeMillis()
        ))
        
        Utils.showNotification("📞 Incoming Call", "From: ${callerNumber ?: "Unknown"}")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (currentCall == call) {
            currentCall = null
            callerNumber = null
            Log.d("CallController", "📞 Call ended")
            
            Utils.sendToLinux(mapOf(
                "event" to "call_ended",
                "timestamp" to System.currentTimeMillis()
            ))
        }
    }
}
