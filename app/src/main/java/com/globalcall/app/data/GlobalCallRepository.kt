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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    ): ListenerRegistration = db.collection("users")
        .addSnapshotListener { snapshot, error ->
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
                    video = it.getBoolean("video") ?: true
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

    fun observeCallStatus(
        callId: String,
        onChange: (String) -> Unit
    ): ListenerRegistration = db.collection("calls").document(callId)
        .addSnapshotListener { snapshot, _ ->
            snapshot?.getString("status")?.let(onChange)
        }

    fun observeBlockedUsers(
        uid: String,
        onChange: (Set<String>) -> Unit
    ): ListenerRegistration = db.collection("blocks")
        .whereEqualTo("blockerUid", uid)
        .addSnapshotListener { snapshot, _ ->
            onChange(snapshot?.documents.orEmpty().mapNotNull { it.getString("blockedUid") }.toSet())
        }

    suspend fun startCall(peer: AppUser, video: Boolean): CallSession {
        val response = authenticatedPost(
            path = "/api/calls/start",
            body = JSONObject()
                .put("calleeUid", peer.uid)
                .put("video", video)
        )
        return CallSession(
            callId = response.getString("callId"),
            peerUid = peer.uid,
            peerName = peer.displayName.ifBlank { peer.email },
            serverUrl = response.getString("serverUrl"),
            token = response.getString("participantToken"),
            video = video,
            outgoing = true
        )
    }

    suspend fun acceptCall(invite: CallInvite): CallSession {
        db.collection("calls").document(invite.id)
            .update(
                mapOf(
                    "status" to "accepted",
                    "acceptedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        val token = requestToken(invite.id)
        return CallSession(
            callId = invite.id,
            peerUid = invite.callerUid,
            peerName = invite.callerName,
            serverUrl = token.first,
            token = token.second,
            video = invite.video,
            outgoing = false
        )
    }

    suspend fun declineCall(callId: String) {
        db.collection("calls").document(callId)
            .update(
                mapOf(
                    "status" to "declined",
                    "endedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun endCall(callId: String) {
        runCatching {
            db.collection("calls").document(callId)
                .update(
                    mapOf(
                        "status" to "ended",
                        "endedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }
    }

    suspend fun requestToken(callId: String): Pair<String, String> {
        val response = authenticatedPost(
            path = "/api/token",
            body = JSONObject().put("callId", callId)
        )
        return response.getString("serverUrl") to response.getString("participantToken")
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val cleanName = displayName.trim()
        require(cleanName.length in 2..50) { "Name must be 2-50 characters" }
        require(bio.length <= 120) { "Bio must be 120 characters or less" }

        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
        ).await()
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
        db.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "fcmToken" to token,
                "devicePlatform" to "android",
                "tokenUpdatedAt" to FieldValue.serverTimestamp()
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

    private suspend fun authenticatedPost(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val idToken = user.getIdToken(false).await().token ?: error("Missing Firebase ID token")
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        require(!baseUrl.contains("YOUR_DOMAIN")) {
            "Configure API_BASE_URL in app/build.gradle.kts"
        }

        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                error(message?.ifBlank { null } ?: "Server error $code")
            }
            JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }
}
