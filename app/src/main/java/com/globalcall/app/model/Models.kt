package com.globalcall.app.model

import com.google.firebase.Timestamp

data class AppUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val online: Boolean = false,
    val lastSeen: Timestamp? = null
)

data class CallInvite(
    val id: String,
    val callerUid: String,
    val callerName: String,
    val calleeUid: String,
    val calleeName: String,
    val status: String,
    val video: Boolean = true
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

enum class MainTab {
    Calls,
    People,
    Profile
}
