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
    val createdAt: Timestamp? = null
)

class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun conversationId(a: String, b: String): String = listOf(a, b).sorted().joinToString("__")

    fun observeMessages(
        peerUid: String,
        onChange: (List<ChatMessage>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val id = conversationId(uid, peerUid)
        return db.collection("conversations").document(id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(120)
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
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                onChange(messages)
            }
    }

    suspend fun sendMessage(peerUid: String, text: String) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Not signed in" }
        val clean = text.trim()
        require(peerUid.isNotBlank() && peerUid != uid) { "Invalid recipient" }
        require(clean.isNotBlank()) { "Write a message first" }
        require(clean.length <= 2000) { "Message is too long" }

        val id = conversationId(uid, peerUid)
        val connection = db.collection("connections").document(id).get().await()
        require(connection.exists()) { "Connect with this user before messaging" }

        val participants = listOf(uid, peerUid).sorted()
        val conversation = db.collection("conversations").document(id)
        val message = conversation.collection("messages").document()
        val batch = db.batch()

        batch.set(
            conversation,
            mapOf(
                "participantUids" to participants,
                "lastMessage" to clean.take(160),
                "lastSenderUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        batch.set(
            message,
            mapOf(
                "senderUid" to uid,
                "receiverUid" to peerUid,
                "text" to clean,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
    }
}
