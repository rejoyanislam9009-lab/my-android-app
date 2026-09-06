package com.globalcall.app.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await


data class ChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val receiverUid: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val readAt: Timestamp? = null
)

data class ConversationState(
    val id: String = "",
    val participantUids: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderUid: String = "",
    val updatedAt: Timestamp? = null,
    val unreadFor: List<String> = emptyList(),
    val typingUid: String = "",
    val typingAt: Timestamp? = null
) {
    fun peerUid(currentUid: String): String = participantUids.firstOrNull { it != currentUid }.orEmpty()
    fun isUnread(currentUid: String): Boolean = currentUid in unreadFor
}

class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun conversationId(a: String, b: String): String = listOf(a, b).sorted().joinToString("__")

    private fun stateFrom(id: String, data: Map<String, Any?>): ConversationState {
        @Suppress("UNCHECKED_CAST")
        val participants = (data["participantUids"] as? List<String>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val unreadFor = (data["unreadFor"] as? List<String>).orEmpty()
        return ConversationState(
            id = id,
            participantUids = participants,
            lastMessage = data["lastMessage"] as? String ?: "",
            lastSenderUid = data["lastSenderUid"] as? String ?: "",
            updatedAt = data["updatedAt"] as? Timestamp,
            unreadFor = unreadFor,
            typingUid = data["typingUid"] as? String ?: "",
            typingAt = data["typingAt"] as? Timestamp
        )
    }

    fun observeMessages(
        peerUid: String,
        onChange: (List<ChatMessage>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val id = conversationId(uid, peerUid)
        return db.collection("conversations").document(id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(160)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents.orEmpty().map { doc ->
                    ChatMessage(
                        id = doc.id,
                        senderUid = doc.getString("senderUid").orEmpty(),
                        receiverUid = doc.getString("receiverUid").orEmpty(),
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt"),
                        readAt = doc.getTimestamp("readAt")
                    )
                }
                onChange(messages)
            }
    }

    fun observeConversation(
        peerUid: String,
        onChange: (ConversationState?) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val id = conversationId(uid, peerUid)
        return db.collection("conversations").document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) onChange(null)
            else onChange(stateFrom(snapshot.id, snapshot.data.orEmpty()))
        }
    }

    fun observeConversations(
        onChange: (List<ConversationState>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        return db.collection("conversations")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val states = snapshot?.documents.orEmpty()
                    .map { stateFrom(it.id, it.data.orEmpty()) }
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                onChange(states)
            }
    }

    suspend fun sendMessage(peerUid: String, text: String) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val clean = text.trim()
        require(peerUid.isNotBlank() && peerUid != uid) { "Invalid recipient" }
        require(clean.isNotBlank()) { "Write a message first" }
        require(clean.length <= 2000) { "Message is too long" }

        val blockedByMe = db.collection("blocks").document("${uid}_$peerUid").get().await().exists()
        require(!blockedByMe) { "Unblock this contact before messaging" }

        val id = conversationId(uid, peerUid)
        val connection = db.collection("connections").document(id).get().await()
        require(connection.exists()) { "Connect with this user before messaging" }

        val participants = listOf(uid, peerUid).sorted()
        val conversation = db.collection("conversations").document(id)
        val message = conversation.collection("messages").document()

        // Keep the security-critical message write separate from optional UI metadata.
        // Older deployed rules may reject typing/unread metadata even though the message
        // itself is allowed. A metadata rejection must not roll back a valid message.
        val conversationSnapshot = conversation.get().await()
        if (!conversationSnapshot.exists()) {
            conversation.set(
                mapOf("participantUids" to participants),
                SetOptions.merge()
            ).await()
        }

        message.set(
            mapOf(
                "senderUid" to uid,
                "receiverUid" to peerUid,
                "text" to clean,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()

        runCatching {
            conversation.set(
                mapOf(
                    "participantUids" to participants,
                    "lastMessage" to clean.take(180),
                    "lastSenderUid" to uid,
                    "unreadFor" to listOf(peerUid),
                    "typingUid" to "",
                    "typingAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun markRead(peerUid: String) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val id = conversationId(uid, peerUid)
        val conversation = db.collection("conversations").document(id)
        val unread = conversation.collection("messages")
            .whereEqualTo("receiverUid", uid)
            .limit(160)
            .get()
            .await()
            .documents
            .filter { it.getTimestamp("readAt") == null }

        if (unread.isNotEmpty()) {
            val batch = db.batch()
            unread.forEach { batch.update(it.reference, "readAt", FieldValue.serverTimestamp()) }
            batch.set(
                conversation,
                mapOf(
                    "unreadFor" to emptyList<String>(),
                    "updatedAt" to (conversation.get().await().getTimestamp("updatedAt") ?: FieldValue.serverTimestamp())
                ),
                SetOptions.merge()
            )
            batch.commit().await()
        } else {
            val snapshot = conversation.get().await()
            @Suppress("UNCHECKED_CAST")
            val unreadFor = (snapshot.get("unreadFor") as? List<String>).orEmpty()
            if (uid in unreadFor) {
                conversation.set(mapOf("unreadFor" to emptyList<String>()), SetOptions.merge()).await()
            }
        }
    }

    suspend fun setTyping(peerUid: String, typing: Boolean) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val id = conversationId(uid, peerUid)
        val connection = db.collection("connections").document(id).get().await()
        if (!connection.exists()) return
        if (typing) {
            val blockedByMe = runCatching {
                db.collection("blocks").document("${uid}_$peerUid").get().await().exists()
            }.getOrDefault(false)
            if (blockedByMe) return
        }
        db.collection("conversations").document(id).set(
            mapOf(
                "participantUids" to listOf(uid, peerUid).sorted(),
                "typingUid" to if (typing) uid else "",
                "typingAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }
}
