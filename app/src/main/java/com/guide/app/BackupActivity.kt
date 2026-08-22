package com.guide.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupActivity : AppCompatActivity() {
    private lateinit var store: GuideStore

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { GuideBackupManager.writeManualBackup(this, store, uri) }
            .onSuccess { Toast.makeText(this, "ব্যাকআপ ফাইল সেভ হয়েছে", Toast.LENGTH_SHORT).show(); render() }
            .onFailure { Toast.makeText(this, "ব্যাকআপ সেভ করা যায়নি", Toast.LENGTH_LONG).show() }
    }

    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        AlertDialog.Builder(this)
            .setTitle("ব্যাকআপ রিস্টোর করবেন?")
            .setMessage("বর্তমান রুটিন, হাজিরা, হিসাব, অ্যালার্ম ও নামাজের সেটিংস ব্যাকআপের ডাটা দিয়ে আপডেট হবে।")
            .setPositiveButton("রিস্টোর") { _, _ ->
                val ok = GuideBackupManager.restoreFromUri(this, store, uri)
                if (ok) {
                    ReminderScheduler.scheduleAll(this, store)
                    GuideBackupManager.autoBackupIfNeeded(this, store, force = true)
                    CloudSyncManager.scheduleUpload(this)
                    Toast.makeText(this, "ডাটা রিস্টোর হয়েছে", Toast.LENGTH_LONG).show()
                    render()
                } else Toast.makeText(this, "সঠিক Guide backup ফাইল নয়", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuideStore(this)
        render()
    }

    private fun render() {
        store = GuideStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(34))
            background = gradient("#08101F", "#111A35")
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("‹", "#1B294A") { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        title.addView(text("ব্যাকআপ ও রিস্টোর", 25f, Color.WHITE, true))
        title.addView(text("NEW • Local + Firebase cloud protection", 12f, Color.parseColor("#8D9BC1")))
        top.addView(title)
        root.addView(top)
        root.addView(space(22))

        if (CloudSyncManager.isSignedIn()) {
            val firebase = card()
            firebase.addView(text("Firebase Cloud Backup  NEW", 18f, Color.WHITE, true))
            firebase.addView(text(CloudSyncManager.currentEmail(), 13f, Color.parseColor("#79D1B5"), true).apply { setPadding(0, dp(4), 0, dp(5)) })
            firebase.addView(text("রুটিন, হাজিরা, হিসাব, alarm, prayer settings ও Guide data এই account-এর UID অনুযায়ী cloud-এ রাখা হবে। PIN/password cloud snapshot-এ রাখা হয় না।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, 0, 0, dp(12)) })
            firebase.addView(button("এখনই ক্লাউড ব্যাকআপ নিন", "#5C4EE0") {
                CloudSyncManager.uploadNow(this) { _, message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            })
            firebase.addView(space(9))
            firebase.addView(button("ক্লাউড থেকে সর্বশেষ ডাটা রিস্টোর", "#267B68") {
                AlertDialog.Builder(this)
                    .setTitle("Cloud restore করবেন?")
                    .setMessage("এই Firebase account-এর সর্বশেষ Guide backup দিয়ে ফোনের Guide data আপডেট হবে।")
                    .setPositiveButton("রিস্টোর") { _, _ ->
                        CloudSyncManager.restoreLatest(this) { ok, message ->
                            if (ok) {
                                store = GuideStore(this)
                                ReminderScheduler.scheduleAll(this, store)
                                GuideBackupManager.autoBackupIfNeeded(this, store, force = true)
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            render()
                        }
                    }.setNegativeButton("বাতিল", null).show()
            })
            firebase.addView(space(9))
            firebase.addView(button("Password reset email পাঠান", "#38527C") {
                val email = CloudSyncManager.currentEmail()
                if (email.isBlank()) Toast.makeText(this, "Account email পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                else FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener { Toast.makeText(this, "Reset email পাঠানো হয়েছে", Toast.LENGTH_LONG).show() }
                    .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Reset email পাঠানো যায়নি", Toast.LENGTH_LONG).show() }
            })
            root.addView(firebase)
            root.addView(space(14))
        } else {
            val firebase = card()
            firebase.addView(text("Firebase Cloud Backup  NEW", 18f, Color.WHITE, true))
            firebase.addView(text("Cloud backup ব্যবহার করতে Email account দিয়ে Guide-এ login করুন।", 13f, Color.parseColor("#94A2C7")))
            root.addView(firebase)
            root.addView(space(14))
        }

        val status = GuideBackupManager.status(this)
        val autoCard = card()
        autoCard.addView(text("অটোমেটিক লোকাল ব্যাকআপ", 18f, Color.WHITE, true))
        autoCard.addView(text("অ্যাপ ব্যবহার করলে প্রতিদিনের সর্বশেষ data ফোনেও backup copy হিসেবে রাখা হবে। Firebase login থাকলে app background/foreground চলাকালীন cloud sync-ও automatic হবে।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(8)) })
        val toggle = CheckBox(this).apply {
            text = "লোকাল অটো ব্যাকআপ চালু"
            setTextColor(Color.WHITE)
            isChecked = status.enabled
            setOnCheckedChangeListener { _, checked ->
                GuideBackupManager.setAutoEnabled(this@BackupActivity, checked)
                if (checked) GuideBackupManager.autoBackupIfNeeded(this@BackupActivity, store, force = true)
                render()
            }
        }
        autoCard.addView(toggle)
        autoCard.addView(text("শেষ লোকাল ব্যাকআপ: ${GuideBackupManager.formattedLastBackup(this)}", 12f, Color.parseColor("#7F90BB")))
        autoCard.addView(text("লোকাল কপি: ${status.count} টি", 12f, Color.parseColor("#7F90BB")).apply { setPadding(0, dp(2), 0, dp(12)) })
        autoCard.addView(button("এখনই লোকাল ব্যাকআপ নিন", "#5C4EE0") {
            GuideBackupManager.autoBackupIfNeeded(this, store, force = true)
            CloudSyncManager.scheduleUpload(this)
            Toast.makeText(this, "ব্যাকআপ আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
            render()
        })
        root.addView(autoCard)
        root.addView(space(14))

        val manualCard = card()
        manualCard.addView(text("ম্যানুয়াল ফাইল ব্যাকআপ", 18f, Color.WHITE, true))
        manualCard.addView(text("Downloads, Files বা Google Drive provider-এ আলাদা JSON backup file রাখতে পারবেন।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(12)) })
        manualCard.addView(button("ব্যাকআপ ফাইল সেভ করুন", "#267B68") {
            val name = "Guide-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.json"
            createBackup.launch(name)
        })
        manualCard.addView(space(9))
        manualCard.addView(button("ব্যাকআপ ফাইল থেকে রিস্টোর", "#38527C") {
            restoreBackup.launch(arrayOf("application/json", "text/plain"))
        })
        if (status.count > 0) {
            manualCard.addView(space(9))
            manualCard.addView(button("সর্বশেষ লোকাল ব্যাকআপ রিস্টোর", "#624A78") {
                AlertDialog.Builder(this).setTitle("সর্বশেষ ব্যাকআপ রিস্টোর")
                    .setMessage("সর্বশেষ লোকাল backup copy থেকে data ফিরিয়ে আনবেন?")
                    .setPositiveButton("রিস্টোর") { _, _ ->
                        if (GuideBackupManager.restoreLatestLocal(this, store)) {
                            ReminderScheduler.scheduleAll(this, store)
                            CloudSyncManager.scheduleUpload(this)
                            Toast.makeText(this, "রিস্টোর সম্পন্ন", Toast.LENGTH_SHORT).show(); render()
                        }
                    }.setNegativeButton("বাতিল", null).show()
            })
        }
        root.addView(manualCard)
        root.addView(space(14))

        val system = card()
        system.addView(text("Android system backup", 18f, Color.WHITE, true))
        system.addView(text("ফোনের Android Backup চালু থাকলে system-level app backup-ও ব্যবহার করতে পারবেন।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(12)) })
        system.addView(button("ফোনের Backup settings খুলুন", "#344B78") {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        root.addView(system)

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded("#17213E", 20)
    }

    private fun button(label: String, bg: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        background = rounded(bg, 14); setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(hex: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex)); cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun space(height: Int) = android.widget.Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
