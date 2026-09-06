package com.globalcall.app.data

import com.globalcall.app.model.AppUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class DiscoveredUser(
    val user: AppUser,
    val username: String = ""
)

class UserDiscoveryRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun normalizeUsername(raw: String): String {
        val clean = raw.trim().removePrefix("@").lowercase(Locale.ROOT)
        require(clean.length in 4..24) { "Username must be 4-24 characters" }
        require(clean.first().isLetter()) { "Username must start with a letter" }
        require(clean.all { it.isLetterOrDigit() || it == '_' }) {
            "Use only letters, numbers and underscore in a username"
        }
        return clean
    }

    private fun generatedUsername(uid: String, displayName: String): String {
        val rawStem = displayName
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
        val stem = rawStem
            .takeIf { it.length >= 3 && it.firstOrNull()?.isLetter() == true }
            ?.take(15)
            ?: "user"
        val suffix = PhoneDirectory.key(uid).take(8).lowercase(Locale.ROOT)
        return "${stem}_$suffix"
    }

    suspend fun ensureMyUsername(): String {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val userRef = db.collection("users").document(user.uid)
        val snapshot = userRef.get().await()
        val existing = snapshot.getString("username").orEmpty()
        if (existing.isNotBlank()) {
            val normalized = runCatching { normalizeUsername(existing) }.getOrNull()
            if (normalized != null) {
                val usernameRef = db.collection("usernames").document(normalized)
                db.runTransaction { tx ->
                    val directory = tx.get(usernameRef)
                    val owner = directory.getString("uid")
                    check(owner == null || owner == user.uid) { "This username is no longer available" }
                    tx.set(
                        usernameRef,
                        mapOf("uid" to user.uid, "updatedAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge()
                    )
                }.await()
                return normalized
            }
        }

        val displayName = snapshot.getString("displayName").orEmpty()
            .ifBlank { user.displayName.orEmpty() }
            .ifBlank { user.email?.substringBefore('@').orEmpty() }
            .ifBlank { "user" }
        val username = generatedUsername(user.uid, displayName)
        val usernameRef = db.collection("usernames").document(username)

        db.runTransaction { tx ->
            val directory = tx.get(usernameRef)
            val owner = directory.getString("uid")
            check(owner == null || owner == user.uid) { "Could not reserve an automatic username" }
            tx.set(
                usernameRef,
                mapOf("uid" to user.uid, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            tx.set(
                userRef,
                mapOf("username" to username, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
        }.await()
        return username
    }

    suspend fun updateMyUsername(raw: String): String {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        val username = normalizeUsername(raw)
        val userRef = db.collection("users").document(user.uid)
        val usernameRef = db.collection("usernames").document(username)

        db.runTransaction { tx ->
            val profile = tx.get(userRef)
            val oldUsername = profile.getString("username").orEmpty()
            val target = tx.get(usernameRef)
            val targetOwner = target.getString("uid")
            check(targetOwner == null || targetOwner == user.uid) { "@$username is already taken" }

            val oldRef = oldUsername
                .takeIf { it.isNotBlank() && it != username }
                ?.let { db.collection("usernames").document(it) }
            val oldDirectory = oldRef?.let { tx.get(it) }

            if (oldRef != null && oldDirectory?.getString("uid") == user.uid) {
                tx.delete(oldRef)
            }
            tx.set(
                usernameRef,
                mapOf("uid" to user.uid, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            tx.set(
                userRef,
                mapOf("username" to username, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
        }.await()
        return username
    }

    private suspend fun usernameFor(uid: String): String = runCatching {
        db.collection("users").document(uid).get().await().getString("username").orEmpty()
    }.getOrDefault("")

    private suspend fun findByUsername(raw: String): DiscoveredUser? {
        val username = normalizeUsername(raw)
        val directory = db.collection("usernames").document(username).get().await()
        if (!directory.exists()) return null
        val uid = directory.getString("uid").orEmpty()
        if (uid.isBlank() || uid == auth.currentUser?.uid) return null
        val core = GlobalCallRepository(auth = auth, db = db)
        val user = core.loadUser(uid) ?: return null
        return DiscoveredUser(user = user, username = username)
    }

    suspend fun findUser(raw: String): DiscoveredUser? {
        val query = raw.trim()
        require(query.isNotBlank()) { "Enter a username, GlobalCall ID or verified phone number" }

        val compact = query.filter(Char::isLetterOrDigit)
        val looksLikeCode = compact.length == 14 && compact.uppercase(Locale.ROOT).startsWith("GC")
        val digits = query.filter(Char::isDigit)
        val looksLikePhone = digits.length >= 8 && query.all {
            it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')'
        }

        val core = GlobalCallRepository(auth = auth, db = db)
        val user = when {
            looksLikeCode -> core.findUserByCode(query)
            looksLikePhone -> core.findUserByPhone(query)
            else -> return findByUsername(query)
        } ?: return null

        return DiscoveredUser(user = user, username = usernameFor(user.uid))
    }

    suspend fun connect(peer: AppUser) {
        val user = requireNotNull(auth.currentUser) { "Not signed in" }
        require(peer.uid.isNotBlank() && peer.uid != user.uid) { "Invalid GlobalCall account" }
        val ids = listOf(user.uid, peer.uid).sorted()
        val connectionId = ids.joinToString("__")
        db.collection("connections").document(connectionId).set(
            mapOf(
                "participantUids" to ids,
                "createdBy" to user.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }
}
