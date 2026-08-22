package com.guide.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject

object CloudSyncManager {
    private const val COLLECTION = "users"
    private const val SNAPSHOT_FIELD = "snapshotJson"
    private const val SYNC_DELAY_MS = 1800L
    private const val META_PREFS = "guide_cloud"
    private const val ACTIVE_UID = "active_uid"
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpload: Runnable? = null

    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null
    fun currentEmail(): String = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
    fun currentUid(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun scheduleUpload(context: Context) {
        if (!isSignedIn()) return
        val app = context.applicationContext
        pendingUpload?.let(handler::removeCallbacks)
        val task = Runnable { uploadNow(app) }
        pendingUpload = task
        handler.postDelayed(task, SYNC_DELAY_MS)
    }

    fun uploadNow(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete?.invoke(false, "লগইন করা নেই")
            return
        }
        bindDeviceToUser(context, user.uid, clearIfDifferent = false)

        val snapshot = buildSnapshot(context)
        val data = hashMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "profileName" to GuideStore(context).profileName(),
            SNAPSHOT_FIELD to snapshot,
            "updatedAt" to FieldValue.serverTimestamp(),
            "appVersion" to "3.0.0"
        )

        FirebaseFirestore.getInstance().collection(COLLECTION).document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                bindDeviceToUser(context, user.uid, clearIfDifferent = false)
                onComplete?.invoke(true, "ক্লাউড ব্যাকআপ সম্পন্ন")
            }
            .addOnFailureListener { e -> onComplete?.invoke(false, firebaseMessage(e)) }
    }

    fun restoreLatest(context: Context, onComplete: (Boolean, String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete(false, "লগইন করা নেই")
            return
        }

        val switchedAccount = bindDeviceToUser(context, user.uid, clearIfDifferent = true)
        FirebaseFirestore.getInstance().collection(COLLECTION).document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val raw = doc.getString(SNAPSHOT_FIELD)
                if (raw.isNullOrBlank()) {
                    onComplete(false, if (switchedAccount) "নতুন অ্যাকাউন্ট—ক্লাউড ব্যাকআপ পাওয়া যায়নি" else "এই অ্যাকাউন্টে আগের কোনো ক্লাউড ব্যাকআপ নেই")
                    return@addOnSuccessListener
                }
                val ok = restoreSnapshot(context, raw)
                bindDeviceToUser(context, user.uid, clearIfDifferent = false)
                onComplete(ok, if (ok) "ক্লাউড ডাটা রিস্টোর হয়েছে" else "ব্যাকআপ ডাটা পড়া যায়নি")
            }
            .addOnFailureListener { e -> onComplete(false, firebaseMessage(e)) }
    }

    fun deleteSession() {
        pendingUpload?.let(handler::removeCallbacks)
        pendingUpload = null
        FirebaseAuth.getInstance().signOut()
    }

    private fun bindDeviceToUser(context: Context, uid: String, clearIfDifferent: Boolean): Boolean {
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val previous = meta.getString(ACTIVE_UID, "").orEmpty()
        val different = previous.isNotBlank() && previous != uid
        if (different && clearIfDifferent) {
            val guidePrefs = context.getSharedPreferences("guide_store", Context.MODE_PRIVATE)
            val oldPinHash = guidePrefs.getString("pin_hash", null)
            val oldPinSalt = guidePrefs.getString("pin_salt", null)
            guidePrefs.edit().clear().apply()
            context.getSharedPreferences("guide_ui", Context.MODE_PRIVATE).edit().clear().apply()
            if (!oldPinHash.isNullOrBlank()) {
                guidePrefs.edit().putString("pin_hash", oldPinHash).putString("pin_salt", oldPinSalt).apply()
            }
        }
        meta.edit().putString(ACTIVE_UID, uid).apply()
        return different
    }

    private fun buildSnapshot(context: Context): String {
        val root = JSONObject()
        root.put("format", 1)
        root.put("guideStore", prefsToJson(context.getSharedPreferences("guide_store", Context.MODE_PRIVATE), setOf("pin_hash", "pin_salt")))
        root.put("guideUi", prefsToJson(context.getSharedPreferences("guide_ui", Context.MODE_PRIVATE), emptySet()))
        root.put("createdAt", System.currentTimeMillis())
        return root.toString()
    }

    private fun restoreSnapshot(context: Context, raw: String): Boolean = runCatching {
        val root = JSONObject(raw)
        val guidePrefs = context.getSharedPreferences("guide_store", Context.MODE_PRIVATE)
        val oldPinHash = guidePrefs.getString("pin_hash", null)
        val oldPinSalt = guidePrefs.getString("pin_salt", null)
        guidePrefs.edit().clear().apply()
        jsonToPrefs(root.optJSONObject("guideStore") ?: JSONObject(), guidePrefs, setOf("pin_hash", "pin_salt"))
        if (!oldPinHash.isNullOrBlank()) guidePrefs.edit().putString("pin_hash", oldPinHash).putString("pin_salt", oldPinSalt).apply()

        val uiPrefs = context.getSharedPreferences("guide_ui", Context.MODE_PRIVATE)
        uiPrefs.edit().clear().apply()
        jsonToPrefs(root.optJSONObject("guideUi") ?: JSONObject(), uiPrefs, emptySet())
        true
    }.getOrDefault(false)

    private fun prefsToJson(prefs: SharedPreferences, excluded: Set<String>): JSONObject {
        val out = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (key in excluded || value == null) return@forEach
            val item = JSONObject()
            when (value) {
                is String -> { item.put("type", "string"); item.put("value", value) }
                is Int -> { item.put("type", "int"); item.put("value", value) }
                is Long -> { item.put("type", "long"); item.put("value", value) }
                is Float -> { item.put("type", "float"); item.put("value", value.toDouble()) }
                is Boolean -> { item.put("type", "boolean"); item.put("value", value) }
                is Set<*> -> { item.put("type", "set"); item.put("value", JSONArray(value.filterIsInstance<String>())) }
                else -> return@forEach
            }
            out.put(key, item)
        }
        return out
    }

    private fun jsonToPrefs(obj: JSONObject, prefs: SharedPreferences, excluded: Set<String>) {
        val editor = prefs.edit()
        obj.keys().forEach { key ->
            if (key in excluded) return@forEach
            val item = obj.optJSONObject(key) ?: return@forEach
            when (item.optString("type")) {
                "string" -> editor.putString(key, item.optString("value", ""))
                "int" -> editor.putInt(key, item.optInt("value", 0))
                "long" -> editor.putLong(key, item.optLong("value", 0L))
                "float" -> editor.putFloat(key, item.optDouble("value", 0.0).toFloat())
                "boolean" -> editor.putBoolean(key, item.optBoolean("value", false))
                "set" -> {
                    val arr = item.optJSONArray("value") ?: JSONArray()
                    val values = mutableSetOf<String>()
                    for (i in 0 until arr.length()) values.add(arr.optString(i))
                    editor.putStringSet(key, values)
                }
            }
        }
        editor.apply()
    }

    private fun firebaseMessage(error: Exception): String {
        val raw = error.localizedMessage.orEmpty()
        return when {
            raw.contains("PERMISSION_DENIED", true) -> "Firestore Rules এখনো অনুমতি দিচ্ছে না"
            raw.contains("network", true) -> "ইন্টারনেট সংযোগ পরীক্ষা করুন"
            else -> raw.ifBlank { "Firebase কাজটি সম্পন্ন হয়নি" }
        }
    }
}
