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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(34))
            background = gradient("#08101F", "#111A35")
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("‹", "#1B294A") { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        title.addView(text("ব্যাকআপ ও রিস্টোর", 25f, Color.WHITE, true))
        title.addView(text("NEW • আপনার Guide ডাটা নিরাপদ রাখুন", 12f, Color.parseColor("#8D9BC1")))
        top.addView(title)
        root.addView(top)
        root.addView(space(22))

        val status = GuideBackupManager.status(this)
        val autoCard = card()
        autoCard.addView(text("অটোমেটিক দৈনিক ব্যাকআপ", 18f, Color.WHITE, true))
        autoCard.addView(text("অ্যাপ ব্যবহার করলে প্রতিদিনের সর্বশেষ ডাটা ফোনে নিরাপদ backup file হিসেবে রাখা হবে।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(8)) })
        val toggle = CheckBox(this).apply {
            text = "অটো ব্যাকআপ চালু"
            setTextColor(Color.WHITE)
            isChecked = status.enabled
            setOnCheckedChangeListener { _, checked ->
                GuideBackupManager.setAutoEnabled(this@BackupActivity, checked)
                if (checked) GuideBackupManager.autoBackupIfNeeded(this@BackupActivity, store, force = true)
                render()
            }
        }
        autoCard.addView(toggle)
        autoCard.addView(text("শেষ ব্যাকআপ: ${GuideBackupManager.formattedLastBackup(this)}", 12f, Color.parseColor("#7F90BB")))
        autoCard.addView(text("লোকাল কপি: ${status.count} টি", 12f, Color.parseColor("#7F90BB")).apply { setPadding(0, dp(2), 0, dp(12)) })
        autoCard.addView(button("এখনই ব্যাকআপ নিন", "#5C4EE0") {
            GuideBackupManager.autoBackupIfNeeded(this, store, force = true)
            Toast.makeText(this, "ব্যাকআপ আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
            render()
        })
        root.addView(autoCard)
        root.addView(space(14))

        val manualCard = card()
        manualCard.addView(text("ম্যানুয়াল ব্যাকআপ", 18f, Color.WHITE, true))
        manualCard.addView(text("ফাইলটি Downloads, Files বা Google Drive provider-এ সেভ করতে পারবেন।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(12)) })
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
                    .setMessage("সর্বশেষ লোকাল backup copy থেকে ডাটা ফিরিয়ে আনবেন?")
                    .setPositiveButton("রিস্টোর") { _, _ ->
                        if (GuideBackupManager.restoreLatestLocal(this, store)) {
                            ReminderScheduler.scheduleAll(this, store)
                            Toast.makeText(this, "রিস্টোর সম্পন্ন", Toast.LENGTH_SHORT).show(); render()
                        }
                    }.setNegativeButton("বাতিল", null).show()
            })
        }
        root.addView(manualCard)
        root.addView(space(14))

        val cloud = card()
        cloud.addView(text("Google/Android backup", 18f, Color.WHITE, true))
        cloud.addView(text("NEW • ফোনের Android Backup চালু থাকলে Android নিজেও app data আপনার Google account-এর system backup-এ রাখতে পারে। এটি Guide-এর নিজস্ব email/password cloud account নয়।", 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(12)) })
        cloud.addView(button("ফোনের Backup settings খুলুন", "#344B78") {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        root.addView(cloud)
        root.addView(space(14))

        val security = card()
        security.addView(text("Cloud account", 18f, Color.WHITE, true))
        security.addView(text("ইমেইল/পাসওয়ার্ড, Forgot password email এবং server cloud-sync চালাতে Firebase/Supabase project configuration প্রয়োজন। Config ছাড়া ভুয়া recovery system চালু করা হয়নি।", 13f, Color.parseColor("#94A2C7")))
        root.addView(security)

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
