package com.globalcall.app.data

import com.globalcall.app.BuildConfig
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

class GlobalCallRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    fun observePeople(
        onChange: (List<AppUser>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration = db.collection("users").addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        val me = currentUid
        val users = snapshot?.documents.orEmpty().mapNotNull { doc ->
            val uid = doc.getString("uid") ?: doc.id
            if (uid == me) null else AppUser(
                uid = uid,
                displayName = doc.getString("displayName").orEmpty(),
                email = doc.getString("email").orEmpty(),
                bio = doc.getString("bio").orEmpty(),
                photoUrl = doc.getString("photoUrl").orEmpty(),
                online = doc.getBoolean("online") ?: false,
                lastSeen = doc.getTimestamp("lastSeen")
            )
        }
        onChange(users.sortedWith(compareByDescending<AppUser> { it.online }.thenBy { it.displayName.lowercase(Locale.ROOT) }))
    }

    fun observeIncomingCall(
        uid: String,
        onChange: (CallInvite?) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration = db.collection("calls")
        .whereEqualTo("calleeUid", uid)
        .whereEqualTo("status", "ringing")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val doc = snapshot?.documents?.maxByOrNull { it.getTimestamp("createdAt")?.seconds ?: 0 }
            onChange(doc?.let {
                CallInvite(
                    id = it.id,
                    callerUid = it.getString("callerUid").orEmpty(),
                    callerName = it.getString("callerName").orEmpty(),
                    calleeUid = it.getString("calleeUid").orEmpty(),
                    calleeName = it.getString("calleeName").orEmpty(),
                    status = it.getString("status").orEmpty(),
                    video = it.getBoolean("video") ?: true,
                    roomName = it.getString("roomName").orEmpty()
                )
            })
        }

    fun observeCallHistory(
        uid: String,
        onChange: (List<CallRecord>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration = db.collection("calls")
        .whereArrayContains("participantUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val calls = snapshot?.documents.orEmpty().map { doc ->
                CallRecord(
                    id = doc.id,
                    callerUid = doc.getString("callerUid").orEmpty(),
                    callerName = doc.getString("callerName").orEmpty(),
                    calleeUid = doc.getString("calleeUid").orEmpty(),
                    calleeName = doc.getString("calleeName").orEmpty(),
                    status = doc.getString("status").orEmpty(),
                    video = doc.getBoolean("video") ?: true,
                    createdAt = doc.getTimestamp("createdAt"),
                    acceptedAt = doc.getTimestamp("acceptedAt"),
                    endedAt = doc.getTimestamp("endedAt")
                )
            }.sortedByDescending { it.createdAt?.seconds ?: 0 }
            onChange(calls.take(60))
        }

    fun observeCallStatus(callId: String, onChange: (String) -> Unit): ListenerRegistration =
        db.collection("calls").document(callId).addSnapshotListener { snapshot, _ ->
            snapshot?.getString("status")?.let(onChange)
        }

    fun observeBlockedUsers(uid: String, onChange: (Set<String>) -> Unit): ListenerRegistration =
        db.collection("blocks").whereEqualTo("blockerUid", uid).addSnapshotListener { snapshot, _ ->
            onChange(snapshot?.documents.orEmpty().mapNotNull { it.getString("blockedUid") }.toSet())
        }

    suspend fun startCall(peer: AppUser, video: Boolean): CallSession {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        require(peer.uid.isNotBlank()) { "Missing contact" }

        val blockedByMe = runCatching { db.collection("blocks").document("${user.uid}_${peer.uid}").get().await().exists() }.getOrDefault(false)
        val blockedByPeer = runCatching { db.collection("blocks").document("${peer.uid}_${user.uid}").get().await().exists() }.getOrDefault(false)
        require(!blockedByMe && !blockedByPeer) { "Calling is unavailable for this contact" }

        val callRef = db.collection("calls").document()
        val roomName = "GlobalCall-${callRef.id}"
        val callerName = user.displayName ?: user.email.orEmpty()
        callRef.set(
            mapOf(
                "callerUid" to user.uid,
                "callerName" to callerName,
                "calleeUid" to peer.uid,
                "calleeName" to peer.displayName.ifBlank { peer.email },
                "participantUids" to listOf(user.uid, peer.uid),
                "roomName" to roomName,
                "status" to "ringing",
                "video" to video,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        return CallSession(
            callId = callRef.id,
            peerUid = peer.uid,
            peerName = peer.displayName.ifBlank { peer.email },
            serverUrl = BuildConfig.MEETING_BASE_URL,
            token = roomName,
            video = video,
            outgoing = true
        )
    }

    suspend fun acceptCall(invite: CallInvite): CallSession {
        db.collection("calls").document(invite.id).update(
            mapOf(
                "status" to "accepted",
                "acceptedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return CallSession(
            callId = invite.id,
            peerUid = invite.callerUid,
            peerName = invite.callerName,
            serverUrl = BuildConfig.MEETING_BASE_URL,
            token = invite.roomName.ifBlank { "GlobalCall-${invite.id}" },
            video = invite.video,
            outgoing = false
        )
    }

    suspend fun loadInvite(callId: String): CallInvite? {
        val uid = currentUid ?: return null
        val doc = db.collection("calls").document(callId).get().await()
        if (!doc.exists() || doc.getString("calleeUid") != uid) return null
        return CallInvite(
            id = doc.id,
            callerUid = doc.getString("callerUid").orEmpty(),
            callerName = doc.getString("callerName").orEmpty(),
            calleeUid = doc.getString("calleeUid").orEmpty(),
            calleeName = doc.getString("calleeName").orEmpty(),
            status = doc.getString("status").orEmpty(),
            video = doc.getBoolean("video") ?: true,
            roomName = doc.getString("roomName").orEmpty()
        )
    }

    suspend fun declineCall(callId: String) {
        db.collection("calls").document(callId).update(
            mapOf(
                "status" to "declined",
                "endedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun endCall(callId: String) {
        runCatching {
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to "ended",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val cleanName = displayName.trim()
        require(cleanName.length in 2..50) { "Name must be 2-50 characters" }
        require(bio.length <= 120) { "Bio must be 120 characters or less" }
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()).await()
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to cleanName,
                "email" to user.email.orEmpty().lowercase(Locale.ROOT),
                "bio" to bio.trim(),
                "locale" to Locale.getDefault().toLanguageTag(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun saveFcmToken(token: String) {
        val uid = currentUid ?: return
        db.collection("devices").document(uid).set(
            mapOf(
                "uid" to uid,
                "fcmToken" to token,
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun setOnline(online: Boolean) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: user.email.orEmpty()),
                "email" to user.email.orEmpty().lowercase(Locale.ROOT),
                "online" to online,
                "lastSeen" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun blockUser(peerUid: String) {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        require(peerUid != uid)
        db.collection("blocks").document("${uid}_$peerUid").set(
            mapOf(
                "blockerUid" to uid,
                "blockedUid" to peerUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun unblockUser(peerUid: String) {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        db.collection("blocks").document("${uid}_$peerUid").delete().await()
    }

    suspend fun reportUser(peerUid: String, reason: String) {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        db.collection("reports").add(
            mapOf(
                "reporterUid" to uid,
                "reportedUid" to peerUid,
                "reason" to reason.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "status" to "open"
            )
        ).await()
    }
}
