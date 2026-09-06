package com.globalcall.app.calls

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object CallStateHealth {
    private val terminal = setOf("ended", "declined", "busy", "missed")

    /**
     * Repairs the signed-in user's callState after an Activity/process interruption.
     * Only the user's own state and calls they participate in are touched.
     */
    suspend fun repair(context: Context) {
        val app = FirebaseApp.getApps(context).firstOrNull()
            ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
            ?: return
        val auth = FirebaseAuth.getInstance(app)
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance(app)
        val userRef = db.collection("users").document(uid)
        val profile = runCatching { userRef.get().await() }.getOrNull() ?: return
        if (!profile.exists()) return

        val state = profile.getString("callState").orEmpty()
        val callId = profile.getString("currentCallId").orEmpty()
        if (state !in setOf("calling", "ringing", "active")) return

        if (callId.isBlank()) {
            clear(userRef)
            return
        }

        // A live process-level WebRTC engine is authoritative for an accepted call.
        if (ActiveCallEngineStore.activeCallId() == callId) return

        val callRef = db.collection("calls").document(callId)
        val call = runCatching { callRef.get().await() }.getOrNull()
        if (call == null || !call.exists()) {
            clear(userRef)
            return
        }

        val status = call.getString("status").orEmpty()
        if (status in terminal) {
            clear(userRef)
            return
        }

        if (status == "accepted") {
            // If no media engine survived, the call cannot actually continue. End it
            // cleanly so both participants can call again instead of staying "busy".
            runCatching {
                callRef.update(
                    mapOf(
                        "status" to "ended",
                        "endedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            clear(userRef)
            return
        }

        if (status == "ringing") {
            val created = call.getTimestamp("createdAt")?.seconds ?: 0L
            val age = if (created > 0L) {
                ((System.currentTimeMillis() / 1000L) - created).coerceAtLeast(0L)
            } else Long.MAX_VALUE
            if (age > 75L) {
                val next = if (call.getString("callerUid") == uid) "missed" else "declined"
                runCatching {
                    callRef.update(
                        mapOf(
                            "status" to next,
                            "endedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                clear(userRef)
            }
        }
    }

    private suspend fun clear(userRef: com.google.firebase.firestore.DocumentReference) {
        userRef.set(
            mapOf(
                "callState" to "idle",
                "currentCallId" to "",
                "callStateUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }
}
