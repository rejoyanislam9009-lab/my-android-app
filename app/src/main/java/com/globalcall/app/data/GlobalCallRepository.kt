package com.globalcall.app.data

import android.content.Context
import android.content.SharedPreferences
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

    private fun accountDisplayName(name: String, email: String = "", fallback: String = "GlobalCall user"): String =
        name.trim().ifBlank { email.substringBefore('@').trim() }.ifBlank { fallback }

    private fun contactPrefs(ownerUid: String): SharedPreferences =
        auth.app.applicationContext.getSharedPreferences(
            "globalcall_contact_names_$ownerUid",
            Context.MODE_PRIVATE
        )

    fun getContactAlias(peerUid: String): String {
        val ownerUid = currentUid ?: return ""
        if (peerUid.isBlank() || peerUid == ownerUid) return ""
        return contactPrefs(ownerUid).getString(peerUid, "").orEmpty().trim()
    }

    fun setContactAlias(peerUid: String, alias: String) {
        val ownerUid = requireNotNull(currentUid) { "Not signed in" }
        require(peerUid.isNotBlank() && peerUid != ownerUid) { "Invalid contact" }
        val clean = alias.trim()
        require(clean.length <= 50) { "Contact name must be 50 characters or less" }
        contactPrefs(ownerUid).edit().apply {
            if (clean.isBlank()) remove(peerUid) else putString(peerUid, clean)
        }.apply()
    }

    private fun applyContactAlias(user: AppUser): AppUser {
        val official = accountDisplayName(user.displayName, user.email)
        val alias = getContactAlias(user.uid)
        return user.copy(displayName = alias.ifBlank { official })
    }

    private fun visiblePeerName(peerUid: String, official: String, email: String = ""): String =
        getContactAlias(peerUid).ifBlank {
            accountDisplayName(official, email, "GlobalCall contact")
        }

    private fun userFrom(doc: DocumentSnapshot): AppUser {
        val uid = doc.getString("uid") ?: doc.id
        val email = doc.getString("email").orEmpty()
        val displayName = accountDisplayName(doc.getString("displayName").orEmpty(), email)
        return AppUser(
            uid = uid,
            displayName = displayName,
            email = email,
            bio = doc.getString("bio").orEmpty(),
            photoUrl = doc.getString("photoUrl").orEmpty(),
            photoData = doc.getString("photoData").orEmpty(),
            phoneLast4 = doc.getString("phoneLast4").orEmpty(),
            callCode = doc.getString("callCode").orEmpty(),
            online = doc.getBoolean("online") ?: false,
            lastSeen = doc.getTimestamp("lastSeen"),
            callState = doc.getString("callState").orEmpty().ifBlank { "idle" },
            currentCallId = doc.getString("currentCallId").orEmpty()
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

    private fun connectionId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("__")

    private fun isBusyProfile(doc: DocumentSnapshot): Boolean {
        val state = doc.getString("callState").orEmpty()
        if (state !in setOf("calling", "ringing", "active")) return false
        val updated = doc.getTimestamp("callStateUpdatedAt")?.seconds ?: return false
        val ageSeconds = ((System.currentTimeMillis() / 1000L) - updated).coerceAtLeast(0L)
        return when (state) {
            // ActiveCallService refreshes a real accepted call every 30 seconds.
            // If that heartbeat disappears, do not leave the contact locked busy for hours.
            "active" -> ageSeconds < 120L
            else -> ageSeconds < 90L
        }
    }

    suspend fun ensureMyCallCode(): String {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val userRef = db.collection("users").document(user.uid)
        val profileSnapshot = userRef.get().await()
        val existing = profileSnapshot.getString("callCode").orEmpty()
        val photoData = profileSnapshot.getString("photoData").orEmpty()
        val code = existing.takeIf { it.isNotBlank() } ?: generatedCode(user.uid)
        val key = compactCode(code)
        val codeRef = db.collection("callCodes").document(key)
        val displayName = accountDisplayName(
            user.displayName.orEmpty(),
            user.email.orEmpty(),
            user.phoneNumber ?: "GlobalCall user"
        )

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
                    "photoData" to photoData,
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

    fun observeMyProfile(
        uid: String,
        onChange: (AppUser?) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        onChange(snapshot?.takeIf { it.exists() }?.let(::userFrom))
    }

    fun observePeople(
        onChange: (List<AppUser>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        val ownerUid = currentUid
        val prefs = ownerUid?.let(::contactPrefs)
        var latestUsers = emptyList<AppUser>()

        fun emit() {
            onChange(
                latestUsers
                    .map(::applyContactAlias)
                    .sortedWith(
                        compareByDescending<AppUser> { it.online }
                            .thenBy { it.displayName.lowercase(Locale.ROOT) }
                    )
            )
        }

        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emit() }
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)

        val firestoreRegistration = db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val me = currentUid
            latestUsers = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val user = userFrom(doc)
                if (user.uid == me) null else user
            }
            emit()
        }

        return object : ListenerRegistration {
            override fun remove() {
                firestoreRegistration.remove()
                prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            }
        }
    }

    fun observeConnectionUids(
        uid: String,
        onChange: (Set<String>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        var connectionPeers = emptySet<String>()
        var conversationPeers = emptySet<String>()

        fun peersFrom(snapshot: com.google.firebase.firestore.QuerySnapshot?): Set<String> =
            snapshot?.documents.orEmpty().flatMap { doc ->
                @Suppress("UNCHECKED_CAST")
                (doc.get("participantUids") as? List<String>).orEmpty()
            }.filter { it != uid }.toSet()

        fun emit() = onChange(connectionPeers + conversationPeers)

        val connectionRegistration = db.collection("connections")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                connectionPeers = peersFrom(snapshot)
                emit()
            }

        val conversationRegistration = db.collection("conversations")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                conversationPeers = peersFrom(snapshot)
                emit()
            }

        return object : ListenerRegistration {
            override fun remove() {
                connectionRegistration.remove()
                conversationRegistration.remove()
            }
        }
    }

    suspend fun findUserByCode(rawCode: String): AppUser? {
        val me = currentUid
        val key = compactCode(rawCode)
        val codeDoc = db.collection("callCodes").document(key).get().await()
        if (!codeDoc.exists()) return null
        val uid = codeDoc.getString("uid").orEmpty()
        if (uid.isBlank() || uid == me) return null
        val profile = db.collection("users").document(uid).get().await()
        val user = if (profile.exists()) userFrom(profile) else AppUser(
            uid = uid,
            displayName = accountDisplayName(codeDoc.getString("displayName").orEmpty()),
            photoUrl = codeDoc.getString("photoUrl").orEmpty(),
            photoData = codeDoc.getString("photoData").orEmpty(),
            callCode = displayCode(key)
        )
        return applyContactAlias(user)
    }

    suspend fun connectByCode(rawCode: String): AppUser {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val peer = requireNotNull(findUserByCode(rawCode)) {
            "No GlobalCall account found with this ID"
        }
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
        val email = profile?.getString("email").orEmpty()
        val user = AppUser(
            uid = uid,
            displayName = accountDisplayName(
                profile?.getString("displayName").orEmpty().ifBlank { directory.getString("displayName").orEmpty() },
                email
            ),
            email = email,
            bio = profile?.getString("bio").orEmpty(),
            photoUrl = profile?.getString("photoUrl") ?: directory.getString("photoUrl").orEmpty(),
            photoData = profile?.getString("photoData") ?: directory.getString("photoData").orEmpty(),
            phoneLast4 = directory.getString("phoneLast4").orEmpty(),
            callCode = profile?.getString("callCode").orEmpty(),
            online = profile?.getBoolean("online") ?: false,
            lastSeen = profile?.getTimestamp("lastSeen"),
            callState = profile?.getString("callState").orEmpty().ifBlank { "idle" },
            currentCallId = profile?.getString("currentCallId").orEmpty()
        )
        return applyContactAlias(user)
    }

    suspend fun publishPhoneDirectory() {
        val user = auth.currentUser ?: return
        val phone = user.phoneNumber ?: return
        val normalized = PhoneDirectory.normalize(phone)
        val displayName = accountDisplayName(user.displayName.orEmpty(), user.email.orEmpty())
        val profile = db.collection("users").document(user.uid).get().await()
        val photoData = profile.getString("photoData").orEmpty()
        db.collection("phoneDirectory").document(PhoneDirectory.key(normalized)).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to displayName,
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "photoData" to photoData,
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
        .whereArrayContains("participantUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val doc = snapshot?.documents.orEmpty()
                .filter { it.getString("calleeUid") == uid && it.getString("status") == "ringing" }
                .maxByOrNull { it.getTimestamp("createdAt")?.seconds ?: 0L }
            onChange(doc?.let {
                val callerUid = it.getString("callerUid").orEmpty()
                CallInvite(
                    id = it.id,
                    callerUid = callerUid,
                    callerName = visiblePeerName(callerUid, it.getString("callerName").orEmpty()),
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
                val callerUid = doc.getString("callerUid").orEmpty()
                val calleeUid = doc.getString("calleeUid").orEmpty()
                val callerRaw = doc.getString("callerName").orEmpty()
                val calleeRaw = doc.getString("calleeName").orEmpty()
                CallRecord(
                    id = doc.id,
                    callerUid = callerUid,
                    callerName = if (callerUid == uid) callerRaw else visiblePeerName(callerUid, callerRaw),
                    calleeUid = calleeUid,
                    calleeName = if (calleeUid == uid) calleeRaw else visiblePeerName(calleeUid, calleeRaw),
                    status = doc.getString("status").orEmpty(),
                    video = doc.getBoolean("video") ?: true,
                    createdAt = doc.getTimestamp("createdAt"),
                    acceptedAt = doc.getTimestamp("acceptedAt"),
                    endedAt = doc.getTimestamp("endedAt")
                )
            }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            onChange(calls.take(80))
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
        require(!isBusyProfile(livePeer)) { "This contact is already on another call" }

        val myProfile = db.collection("users").document(user.uid).get().await()
        require(!isBusyProfile(myProfile)) { "You already have another call in progress" }

        val blockedByMe = runCatching {
            db.collection("blocks").document("${user.uid}_${peer.uid}").get().await().exists()
        }.getOrDefault(false)
        val blockedByPeer = runCatching {
            db.collection("blocks").document("${peer.uid}_${user.uid}").get().await().exists()
        }.getOrDefault(false)
        require(!blockedByMe && !blockedByPeer) { "Calling is unavailable for this contact" }

        val callRef = db.collection("calls").document()
        val roomName = "GlobalCall-${callRef.id}"
        val callerName = accountDisplayName(user.displayName.orEmpty(), user.email.orEmpty(), user.phoneNumber ?: "GlobalCall user")
        val liveEmail = livePeer.getString("email").orEmpty()
        val officialCalleeName = accountDisplayName(
            livePeer.getString("displayName").orEmpty(),
            liveEmail,
            peer.displayName.ifBlank { "GlobalCall user" }
        )
        val localPeerName = getContactAlias(peer.uid).ifBlank {
            peer.displayName.ifBlank { officialCalleeName }
        }

        callRef.set(
            mapOf(
                "callerUid" to user.uid,
                "callerName" to callerName,
                "calleeUid" to peer.uid,
                "calleeName" to officialCalleeName,
                "participantUids" to listOf(user.uid, peer.uid),
                "roomName" to roomName,
                "status" to "ringing",
                "video" to video,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        setMyCallState("calling", callRef.id)

        return CallSession(
            callId = callRef.id,
            peerUid = peer.uid,
            peerName = localPeerName,
            serverUrl = BuildConfig.MEETING_BASE_URL,
            token = roomName,
            video = video,
            outgoing = true
        )
    }

    suspend fun acceptCall(invite: CallInvite): CallSession {
        val uid = requireNotNull(currentUid) { "Not signed in" }
        val me = db.collection("users").document(uid).get().await()
        if (isBusyProfile(me) && me.getString("currentCallId").orEmpty() != invite.id) {
            db.collection("calls").document(invite.id).update(
                mapOf(
                    "status" to "busy",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            error("You already have another call in progress")
        }

        db.collection("calls").document(invite.id).update(
            mapOf(
                "status" to "accepted",
                "acceptedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        setMyCallState("active", invite.id)
        return CallSession(
            invite.id,
            invite.callerUid,
            visiblePeerName(invite.callerUid, invite.callerName),
            BuildConfig.MEETING_BASE_URL,
            invite.roomName.ifBlank { "GlobalCall-${invite.id}" },
            invite.video,
            false
        )
    }

    suspend fun loadInvite(callId: String): CallInvite? {
        val uid = currentUid ?: return null
        val doc = db.collection("calls").document(callId).get().await()
        if (!doc.exists() || doc.getString("calleeUid") != uid) return null
        val callerUid = doc.getString("callerUid").orEmpty()
        return CallInvite(
            id = doc.id,
            callerUid = callerUid,
            callerName = visiblePeerName(callerUid, doc.getString("callerName").orEmpty()),
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
        clearMyCallState(callId)
    }

    suspend fun markMissedCall(callId: String) {
        runCatching {
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to "missed",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        clearMyCallState(callId)
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
        clearMyCallState(callId)
    }

    suspend fun setMyCallState(state: String, callId: String) {
        val uid = currentUid ?: return
        db.collection("users").document(uid).set(
            mapOf(
                "callState" to state,
                "currentCallId" to callId,
                "callStateUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun clearMyCallState(callId: String = "") {
        val uid = currentUid ?: return
        val ref = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val current = tx.get(ref)
            val activeId = current.getString("currentCallId").orEmpty()
            if (callId.isBlank() || activeId.isBlank() || activeId == callId) {
                tx.set(
                    ref,
                    mapOf(
                        "callState" to "idle",
                        "currentCallId" to "",
                        "callStateUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
        }.await()
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val cleanName = displayName.trim()
        require(cleanName.length in 2..50) { "Name must be 2-50 characters" }
        require(bio.length <= 120) { "Bio must be 120 characters or less" }
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
        ).await()
        val callCode = ensureMyCallCode()
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to cleanName,
                "email" to user.email.orEmpty().lowercase(Locale.ROOT),
                "bio" to bio.trim(),
                "callCode" to callCode,
                "locale" to Locale.getDefault().toLanguageTag(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        runCatching { publishPhoneDirectory() }
    }

    suspend fun updateProfilePhoto(photoData: String) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        require(photoData.length <= 260_000) { "Profile photo is too large" }
        val callCode = ensureMyCallCode()
        val key = compactCode(callCode)
        db.collection("users").document(user.uid).set(
            mapOf("photoData" to photoData, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        db.collection("callCodes").document(key).set(
            mapOf("uid" to user.uid, "photoData" to photoData, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
        runCatching { publishPhoneDirectory() }
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
        val userRef = db.collection("users").document(user.uid)
        if (!online) {
            userRef.set(
                mapOf(
                    "online" to false,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "callState" to "idle",
                    "currentCallId" to "",
                    "callStateUpdatedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            return
        }

        val phone = user.phoneNumber
        val code = runCatching { ensureMyCallCode() }.getOrDefault("")
        val existing = runCatching { userRef.get().await() }.getOrNull()
        val data = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "displayName" to accountDisplayName(user.displayName.orEmpty(), user.email.orEmpty(), phone ?: "GlobalCall user"),
            "email" to user.email.orEmpty().lowercase(Locale.ROOT),
            "phoneLast4" to (
                phone?.let { runCatching { PhoneDirectory.last4(PhoneDirectory.normalize(it)) }.getOrDefault("") } ?: ""
            ),
            "online" to true,
            "lastSeen" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (code.isNotBlank()) data["callCode"] = code
        if (existing != null && isBusyProfile(existing)) {
            // Keep a live, recently-heartbeating call state intact across activity recreation.
        } else {
            data["callState"] = "idle"
            data["currentCallId"] = ""
            data["callStateUpdatedAt"] = FieldValue.serverTimestamp()
        }
        userRef.set(data, SetOptions.merge()).await()
        runCatching { publishPhoneDirectory() }
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
