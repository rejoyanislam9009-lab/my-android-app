package com.guide.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var store: GuideStore
    private lateinit var auth: FirebaseAuth
    private var registerMode = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuideStore(this)
        auth = FirebaseAuth.getInstance()
        render()
    }

    private fun render() {
        setContentView(buildScreen())
    }

    private fun buildScreen(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(42), dp(24), dp(40))
            background = gradient("#090E1C", "#121A34")
        }

        root.addView(TextView(this).apply {
            text = "G"
            gravity = Gravity.CENTER
            textSize = 35f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = gradientCard("#7457FF", "#5B8CFF", 26)
            elevation = dp(10).toFloat()
        }, LinearLayout.LayoutParams(dp(82), dp(82)))

        root.addView(space(22))
        root.addView(title(if (auth.currentUser != null) "Guide-এ স্বাগতম" else "Guide Account"))
        root.addView(subtitle(
            if (auth.currentUser != null) "আপনার Firebase account চালু আছে এবং ডাটা ক্লাউডে sync থাকবে।"
            else "Email ও Password দিয়ে লগইন করুন—আপনার ডাটা account অনুযায়ী backup ও restore হবে।"
        ))

        val card = panel()
        if (auth.currentUser != null) {
            if (store.hasProfile()) buildSignedInAccess(card) else buildSignedInRestore(card)
        } else {
            buildCloudAuth(card)
        }
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(space(18))
        root.addView(TextView(this).apply {
            text = "Firebase secure account • Cloud backup & restore"
            textSize = 11.5f
            setTextColor(Color.parseColor("#7380A7"))
            gravity = Gravity.CENTER
        })

        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root)
        }
    }

    private fun buildSignedInAccess(card: LinearLayout) {
        card.addView(kicker("ACCOUNT READY"))
        card.addView(TextView(this).apply {
            text = store.profileName()
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        card.addView(TextView(this).apply {
            text = auth.currentUser?.email ?: "Firebase account"
            textSize = 13f
            setTextColor(Color.parseColor("#91A0C8"))
            setPadding(0, dp(4), 0, dp(16))
        })

        card.addView(primaryButton("Guide খুলুন") {
            CloudSyncManager.scheduleUpload(this)
            openGuide()
        })
        card.addView(space(10))
        card.addView(secondaryButton("অন্য Email account ব্যবহার করুন") {
            CloudSyncManager.uploadNow(this) { _, _ ->
                CloudSyncManager.deleteSession()
                render()
            }
        })
    }

    private fun buildSignedInRestore(card: LinearLayout) {
        card.addView(kicker("CLOUD RESTORE • NEW"))
        card.addView(TextView(this).apply {
            text = auth.currentUser?.email ?: "Firebase account"
            textSize = 14f
            setTextColor(Color.parseColor("#A9B5D6"))
            setPadding(0, 0, 0, dp(12))
        })
        val name = field("আপনার নাম").apply {
            val current = store.profileName()
            if (current != "Guide User") setText(current)
        }
        card.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        card.addView(space(14))
        card.addView(primaryButton("ক্লাউড রিস্টোর করে চালু করুন") {
            if (busy) return@primaryButton
            busy = true
            CloudSyncManager.restoreLatest(this) { restored, message ->
                busy = false
                store = GuideStore(this)
                val restoredName = store.profileName().takeIf { it != "Guide User" }
                val finalName = restoredName ?: name.text.toString().trim().ifBlank {
                    auth.currentUser?.email?.substringBefore("@") ?: "Guide User"
                }
                initializeLocalProfile(finalName)
                toast(message)
                if (!restored) CloudSyncManager.uploadNow(this)
                openGuide()
            }
        })
        card.addView(space(10))
        card.addView(secondaryButton("লগআউট") {
            CloudSyncManager.deleteSession()
            render()
        })
    }

    private fun buildCloudAuth(card: LinearLayout) {
        card.addView(kicker(if (registerMode) "CREATE ACCOUNT • NEW" else "EMAIL LOGIN"))

        val name = if (registerMode) field("আপনার নাম") else null
        val email = field("Email address").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = field("Password", password = true)

        if (name != null) {
            card.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
            card.addView(space(10))
        }
        card.addView(email, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        card.addView(space(10))
        card.addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        card.addView(space(15))

        card.addView(primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            if (busy) return@primaryButton
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (!validEmail(mail) || !validPassword(pass)) return@primaryButton
            if (registerMode) register(name?.text?.toString().orEmpty(), mail, pass)
            else login(mail, pass)
        })

        if (!registerMode) {
            card.addView(space(8))
            card.addView(secondaryButton("পাসওয়ার্ড ভুলে গেছেন? Reset করুন") {
                val mail = email.text.toString().trim()
                if (!validEmail(mail)) return@secondaryButton
                sendPasswordReset(mail)
            })
        }

        card.addView(space(8))
        card.addView(secondaryButton(if (registerMode) "আগে থেকেই account আছে? লগইন" else "নতুন? অ্যাকাউন্ট তৈরি করুন") {
            registerMode = !registerMode
            render()
        })
    }

    private fun register(name: String, email: String, password: String) {
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            toast("আপনার নাম লিখুন")
            return
        }
        busy = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                busy = false
                initializeLocalProfile(cleanName)
                CloudSyncManager.uploadNow(this) { _, message -> toast(message) }
                openGuide()
            }
            .addOnFailureListener {
                busy = false
                toast(firebaseError(it))
            }
    }

    private fun login(email: String, password: String) {
        busy = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                CloudSyncManager.restoreLatest(this) { restored, message ->
                    busy = false
                    store = GuideStore(this)
                    val name = store.profileName().takeIf { it != "Guide User" }
                        ?: email.substringBefore("@").ifBlank { "Guide User" }
                    initializeLocalProfile(name)
                    toast(message)
                    if (!restored) CloudSyncManager.uploadNow(this)
                    openGuide()
                }
            }
            .addOnFailureListener {
                busy = false
                toast(firebaseError(it))
            }
    }

    private fun initializeLocalProfile(name: String) {
        store = GuideStore(this)
        val localSecret = auth.currentUser?.uid ?: "firebase-account"
        store.saveProfile(name, localSecret)
        store.seedDefaultsIfNeeded()
        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.scheduleAll(this, store)
    }

    private fun sendPasswordReset(email: String) {
        if (busy) return
        busy = true
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                busy = false
                AlertDialog.Builder(this)
                    .setTitle("Reset email পাঠানো হয়েছে")
                    .setMessage("$email ঠিকানায় Firebase password reset link পাঠানো হয়েছে। Email খুলে নতুন password সেট করুন, তারপর Guide-এ ফিরে লগইন করুন।")
                    .setPositiveButton("ঠিক আছে", null)
                    .show()
            }
            .addOnFailureListener {
                busy = false
                toast(firebaseError(it))
            }
    }

    private fun openGuide() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun validEmail(value: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            toast("সঠিক Email address লিখুন")
            return false
        }
        return true
    }

    private fun validPassword(value: String): Boolean {
        if (value.length < 6) {
            toast("Password কমপক্ষে 6 অক্ষরের হতে হবে")
            return false
        }
        return true
    }

    private fun firebaseError(error: Exception): String {
        val raw = error.localizedMessage.orEmpty()
        return when {
            raw.contains("password", true) && (raw.contains("invalid", true) || raw.contains("credential", true)) -> "Email বা Password সঠিক নয়"
            raw.contains("credential", true) -> "Email বা Password সঠিক নয়"
            raw.contains("already", true) && raw.contains("email", true) -> "এই Email দিয়ে আগে থেকেই account আছে"
            raw.contains("network", true) -> "ইন্টারনেট সংযোগ পরীক্ষা করুন"
            raw.contains("permission", true) -> "Firebase permission এখনও সম্পূর্ণ হয়নি"
            else -> raw.ifBlank { "Firebase কাজটি সম্পন্ন হয়নি" }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = rounded("#17203D", 24)
        elevation = dp(5).toFloat()
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 31f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }

    private fun subtitle(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.parseColor("#A7B2D4"))
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(26))
    }

    private fun kicker(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#8D9BC5"))
        setPadding(dp(2), 0, 0, dp(12))
    }

    private fun field(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.parseColor("#7582A9"))
        setTextColor(Color.WHITE)
        textSize = 15.5f
        setSingleLine(true)
        inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        setPadding(dp(18), 0, dp(18), 0)
        background = rounded("#0F1730", 15)
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        background = gradientCard("#7457FF", "#5F7CFF", 15)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.parseColor("#C6CFF0"))
        background = rounded("#202B4D", 14)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
    }

    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun gradientCard(start: String, end: String, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun space(height: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
