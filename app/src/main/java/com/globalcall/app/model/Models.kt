package com.globalcall.app.model

import com.google.firebase.Timestamp

data class AppUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val photoData: String = "",
    val phoneLast4: String = "",
    val callCode: String = "",
    val online: Boolean = false,
    val lastSeen: Timestamp? = null,
    val callState: String = "idle",
    val currentCallId: String = ""
)

data class CallInvite(
    val id: String,
    val callerUid: String,
    val callerName: String,
    val calleeUid: String,
    val calleeName: String,
    val status: String,
    val video: Boolean = true,
    val roomName: String = ""
)

data class CallRecord(
    val id: String,
    val callerUid: String,
    val callerName: String,
    val calleeUid: String,
    val calleeName: String,
    val status: String,
    val video: Boolean,
    val createdAt: Timestamp?,
    val acceptedAt: Timestamp?,
    val endedAt: Timestamp?
) {
    fun peerUid(currentUid: String): String = if (callerUid == currentUid) calleeUid else callerUid
    fun peerName(currentUid: String): String = if (callerUid == currentUid) calleeName else callerName
    fun isOutgoing(currentUid: String): Boolean = callerUid == currentUid

    fun durationSeconds(): Long? {
        val start = acceptedAt?.seconds ?: return null
        val end = endedAt?.seconds ?: return null
        return (end - start).coerceAtLeast(0L)
    }

    fun outcomeFor(currentUid: String): String = when (status) {
        "missed" -> if (isOutgoing(currentUid)) "No answer" else "Missed"
        "busy" -> "Busy"
        "declined" -> if (isOutgoing(currentUid)) "Declined" else "You declined"
        "ended" -> "Completed"
        "accepted" -> "Connected"
        "ringing" -> "Ringing"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

data class CallSession(
    val callId: String,
    val peerUid: String,
    val peerName: String,
    val serverUrl: String,
    val token: String,
    val video: Boolean = true,
    val outgoing: Boolean = false
)

// Legacy HomeScreen still uses this enum. The v1.4 ReadyHomeScreen uses its own
// four-position navigation state for Calls / Chats / People / Profile.
enum class MainTab {
    Calls,
    People,
    Profile
}
