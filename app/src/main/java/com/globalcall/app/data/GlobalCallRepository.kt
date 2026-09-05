package com.globalcall.app.data

import com.globalcall.app.BuildConfig
import com.globalcall.app.model.AppUser
import com.globalcall.app.model.CallInvite
import com.globalcall.app.model.CallRecord
import com.globalcall.app.model.CallSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
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

    private fun userFrom(doc: DocumentSnapshot): AppUser {
        val uid = doc.getString("uid") ?: doc.id
        return AppUser(
            uid = uid,
            displayName = doc.getString("displayName").orEmpty(),
            email = doc.getString("email").orEmpty(),
            bio = doc.getString("bio").orEmpty(),
            photoUrl = doc.getString("photoUrl").orEmpty(),
            phoneLast4 = doc.getString("phoneLast4").orEmpty(),
            callCode = doc.getString("callCode").orEmpty(),
            online = doc.getBoolean("online") ?: false,
            lastSeen = doc.getTimestamp("lastSeen")
        )
    }

    private fun compactCode(raw: String): String {
        val compact = raw.trim().uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        require(compact.startsWith("GC") && compact.length == 14) {
            "Enter a valid GlobalCall ID, for example GC-1A2B-3C4D-5E6F"
        }
        return compact
    }

    private fun displayCode(compact: String): String =
        "GC-${compact.substring(2, 6)}-${compact.substring(6, 10)}-${compact.substring(10, 14)}"

    private fun generatedCode(uid: String): String {
        val hash = PhoneDirectory.key(uid).take(12).uppercase(Locale.ROOT)
        return displayCode("GC$hash")
    }

    private fun connectionId(a: String, b: String): String = listOf(a, b).sorted().joinToString("__")

    suspend fun ensureMyCallCode(): String {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val userRef = db.collection("users").document(user.uid)
        val existing = userRef.get().await().getString("callCode").orEmpty()
        val code = existing.takeIf { it.isNotBlank() } ?: generatedCode(user.uid)
        val key = compactCode(code)
        val codeRef = db.collection("callCodes").document(key)
        val displayName = user.displayName ?: user.email?.substringBefore('@') ?: user.phoneNumber ?: "GlobalCall user"

        db.runTransaction { tx ->
            val occupied = tx.get(codeRef)
            val owner = occupied.getString("uid")
            check(owner == null || owner == user.uid) { "Could not reserve this GlobalCall ID" }
            tx.set(
                codeRef,
                mapOf(
                    "uid" to user.uid,
                    "displayName" to displayName,
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            tx.set(
                userRef,
                mapOf(
                    "uid" to user.uid,
                    "displayName" to displayName,
                    "email" to user.email.orEmpty().lowercase(Locale.ROOT),
                    "callCode" to code,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()
        return code
    }

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
            val user = userFrom(doc)
            if (user.uid == me) null else user
        }
        onChange(users.sortedWith(compareByDescending<AppUser> { it.online }.thenBy { it.displayName.lowercase(Locale.ROOT) }))
    }

    fun observeConnectionUids(
        uid: String,
        onChange: (Set<String>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration = db.collection("connections")
        .whereArrayContains("participantUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val peers = snapshot?.documents.orEmpty().flatMap { doc ->
                @Suppress("UNCHECKED_CAST")
                (doc.get("participantUids") as? List<String>).orEmpty()
            }.filter { it != uid }.toSet()
            onChange(peers)
        }

    suspend fun findUserByCode(rawCode: String): AppUser? {
        val me = currentUid
        val key = compactCode(rawCode)
        val codeDoc = db.collection("callCodes").document(key).get().await()
        if (!codeDoc.exists()) return null
        val uid = codeDoc.getString("uid").orEmpty()
        if (uid.isBlank() || uid == me) return null
        val profile = db.collection("users").document(uid).get().await()
        return if (profile.exists()) userFrom(profile) else AppUser(
            uid = uid,
            displayName = codeDoc.getString("displayName").orEmpty(),
            photoUrl = codeDoc.getString("photoUrl").orEmpty(),
            callCode = displayCode(key)
        )
    }

    suspend fun connectByCode(rawCode: String): AppUser {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val peer = requireNotNull(findUserByCode(rawCode)) { "No GlobalCall account found with this ID" }
        val ids = listOf(user.uid, peer.uid).sorted()
        db.collection("connections").document(connectionId(user.uid, peer.uid)).set(
            mapOf(
                "participantUids" to ids,
                "createdBy" to user.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        return peer
    }

    suspend fun findUserByPhone(rawPhone: String): AppUser? {
        val phone = PhoneDirectory.normalize(rawPhone)
        val key = PhoneDirectory.key(phone)
        val directory = db.collection("phoneDirectory").document(key).get().await()
        if (!directory.exists()) return null
        val uid = directory.getString("uid").orEmpty()
        if (uid.isBlank() || uid == currentUid) return null
        val profile = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
        return AppUser(
            uid = uid,
            displayName = profile?.getString("displayName") ?: directory.getString("displayName").orEmpty(),
            email = profile?.getString("email").orEmpty(),
            bio = profile?.getString("bio").orEmpty(),
            photoUrl = profile?.getString("photoUrl") ?: directory.getString("photoUrl").orEmpty(),
            phoneLast4 = directory.getString("phoneLast4").orEmpty(),
            callCode = profile?.getString("callCode").orEmpty(),
            online = profile?.getBoolean("online") ?: false,
            lastSeen = profile?.getTimestamp("lastSeen")
        )
    }

    suspend fun publishPhoneDirectory() {
        val user = auth.currentUser ?: return
        val phone = user.phoneNumber ?: return
        val normalized = PhoneDirectory.normalize(phone)
        val displayName = user.displayName ?: "GlobalCall user"
        db.collection("phoneDirectory").document(PhoneDirectory.key(normalized)).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to displayName,
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "phoneLast4" to PhoneDirectory.last4(normalized),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        db.collection("users").document(user.uid).set(
            mapOf(
                "phoneLast4" to PhoneDirectory.last4(normalized),
                "phoneVerified" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
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
        val connection = db.collection("connections").document(connectionId(user.uid, peer.uid)).get().await()
        require(connection.exists()) { "Connect with this user before calling" }
        val livePeer = db.collection("users").document(peer.uid).get().await()
        require(livePeer.exists()) { "This GlobalCall account is unavailable" }
        require(livePeer.getBoolean("online") == true) { "This contact is offline right now" }
        val blockedByMe = runCatching { db.collection("blocks").document("${user.uid}_${peer.uid}").get().await().exists() }.getOrDefault(false)
        val blockedByPeer = runCatching { db.collection("blocks").document("${peer.uid}_${user.uid}").get().await().exists() }.getOrDefault(false)
        require(!blockedByMe && !blockedByPeer) { "Calling is unavailable for this contact" }
        val callRef = db.collection("calls").document()
        val roomName = "GlobalCall-${callRef.id}"
        val callerName = user.displayName ?: user.phoneNumber ?: user.email.orEmpty()
        val calleeName = livePeer.getString("displayName").orEmpty().ifBlank { peer.displayName.ifBlank { "GlobalCall user" } }
        callRef.set(
            mapOf(
                "callerUid" to user.uid,
                "callerName" to callerName,
                "calleeUid" to peer.uid,
                "calleeName" to calleeName,
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
            peerName = calleeName,
            serverUrl = BuildConfig.MEETING_BASE_URL,
            token = roomName,
            video = video,
            outgoing = true
        )
    }

    suspend fun acceptCall(invite: CallInvite): CallSession {
        db.collection("calls").document(invite.id).update(
            mapOf("status" to "accepted", "acceptedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        return CallSession(invite.id, invite.callerUid, invite.callerName, BuildConfig.MEETING_BASE_URL, invite.roomName.ifBlank { "GlobalCall-${invite.id}" }, invite.video, false)
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
        db.collection("calls").document(callId).update(mapOf("status" to "declined", "endedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun endCall(callId: String) {
        runCatching { db.collection("calls").document(callId).update(mapOf("status" to "ended", "endedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await() }
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val cleanName = displayName.trim()
        require(cleanName.length in 2..50) { "Name must be 2-50 characters" }
        require(bio.length <= 120) { "Bio must be 120 characters or less" }
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()).await()
        val callCode = ensureMyCallCode()
        db.collection("users").document(user.uid).set(
            mapOf("uid" to user.uid, "displayName" to cleanName, "email" to user.email.orEmpty().lowercase(Locale.ROOT), "bio" to bio.trim(), "callCode" to callCode, "locale" to Locale.getDefault().toLanguageTag(), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        runCatching { publishPhoneDirectory() }
    }

    suspend fun saveFcmToken(token: String) {
        val uid = currentUid ?: return
        db.collection("devices").document(uid).set(mapOf("uid" to uid, "fcmToken" to token, "platform" to "android", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    suspend fun setOnline(online: Boolean) {
        val user = auth.currentUser ?: return
        val phone = user.phoneNumber
        val code = if (online) runCatching { ensureMyCallCode() }.getOrDefault("") else ""
        val data = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: phone ?: user.email.orEmpty()),
            "email" to user.email.orEmpty().lowercase(Locale.ROOT),
            "phoneLast4" to (phone?.let { runCatching { PhoneDirectory.last4(PhoneDirectory.normalize(it)) }.getOrDefault("") } ?: ""),
            "online" to online,
            "lastSeen" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (code.isNotBlank()) data["callCode"] = code
        db.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
        if (online) runCatching { publishPhoneDirectory() }
    }

    suspend fun blockUser(peerUid: String) {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        require(peerUid != uid)
        db.collection("blocks").document("${uid}_$peerUid").set(mapOf("blockerUid" to uid, "blockedUid" to peerUid, "createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun unblockUser(peerUid: String) {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        db.collection("blocks").document("${uid}_$peerUid").delete().await()
    }

    suspend fun reportUser(peerUid: String, reason: String) {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        db.collection("reports").add(mapOf("reporterUid" to uid, "reportedUid" to peerUid, "reason" to reason.take(300), "createdAt" to FieldValue.serverTimestamp(), "status" to "open")).await()
    }
}
