from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# MainActivity
p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()

if 'সব অ্যালার্ম ও রিমাইন্ডার একসাথে বন্ধ' not in s:
    s = req(
        s,
        'import androidx.core.content.ContextCompat\n',
        'import androidx.core.content.ContextCompat\nimport androidx.swiperefreshlayout.widget.SwipeRefreshLayout\n',
        'SwipeRefreshLayout import'
    )

    old_scroll = '''        shell.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))'''
    new_scroll = '''        val pageScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }
        val refresh = SwipeRefreshLayout(this).apply {
            setProgressBackgroundColorSchemeColor(Color.parseColor("#17213E"))
            setColorSchemeColors(Color.parseColor("#7257FF"), Color.parseColor("#39C6A3"))
            addView(pageScroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnRefreshListener {
                handler.postDelayed({
                    ReminderScheduler.scheduleAll(this@MainActivity, store)
                    isRefreshing = false
                    render()
                    Toast.makeText(this@MainActivity, "পৃষ্ঠা রিফ্রেশ হয়েছে", Toast.LENGTH_SHORT).show()
                }, 450L)
            }
        }
        shell.addView(refresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))'''
    s = req(s, old_scroll, new_scroll, 'pull to refresh shell')

    settings_anchor = '''        root.addView(space(9)); root.addView(rowCard("¤", "Currency", currency(), "#2F826E") { changeCurrency() })
        root.addView(space(20))
        root.addView(sectionTitle("ডাটা ও রিপোর্ট"))
        root.addView(rowCard("⇧", "ব্যাকআপ ও রিস্টোর  NEW", "অটো ব্যাকআপ, manual save এবং restore", "#6C668E") { startActivity(Intent(this, BackupActivity::class.java)) })'''
    settings_new = '''        root.addView(space(9)); root.addView(rowCard("¤", "Currency", currency(), "#2F826E") { changeCurrency() })
        root.addView(space(20))
        root.addView(sectionTitle("দ্রুত নিয়ন্ত্রণ"))
        root.addView(rowCard("🔕", "সব অ্যালার্ম বন্ধ করুন", "সব অ্যালার্ম ও রিমাইন্ডার একসাথে বন্ধ", "#8D4250") { confirmDisableAllReminders() })
        root.addView(space(9))
        root.addView(rowCard("↻", "Guide রিসেট করুন", "সমস্যা হলে local data ও settings ডিফল্ট করুন", "#6B4B8E") { confirmResetGuide() })
        root.addView(space(20))
        root.addView(sectionTitle("ডাটা ও রিপোর্ট"))
        root.addView(rowCard("⇧", "ব্যাকআপ ও রিস্টোর  NEW", "অটো ব্যাকআপ, manual save এবং restore", "#6C668E") { startActivity(Intent(this, BackupActivity::class.java)) })'''
    s = req(s, settings_anchor, settings_new, 'settings reset and quick off cards')

    marker = '    private fun lockApp() {'
    if marker not in s:
        raise SystemExit('pattern not found: lockApp marker')
    helpers = r'''    private fun confirmDisableAllReminders() {
        AlertDialog.Builder(this)
            .setTitle("সব অ্যালার্ম বন্ধ করবেন?")
            .setMessage("Routine, খাবার, To-do, ওষুধ, Bill, সাধারণ Alarm এবং নামাজ—সব সক্রিয় অ্যালার্ম/রিমাইন্ডার বন্ধ হবে। Data মুছবে না।")
            .setPositiveButton("সব বন্ধ করুন") { _, _ ->
                disableAllGuideReminders()
                Toast.makeText(this, "সব অ্যালার্ম ও রিমাইন্ডার বন্ধ হয়েছে", Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun disableAllGuideReminders(syncCloud: Boolean = true) {
        store.saveRoutines(store.routines().map { it.copy(alarmEnabled = false) })
        store.saveMeals(store.meals().map { it.copy(alarmEnabled = false) })
        store.saveAlarms(store.alarms().map { it.copy(enabled = false) })
        store.setPrayerEnabled(false)
        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach { store.setPrayerAlarmEnabled(it, false) }

        val daily = DailyLifeStore(this)
        daily.saveTodos(daily.todos().map { it.copy(reminderEnabled = false) })
        daily.saveMedicines(daily.medicines().map { it.copy(enabled = false) })
        daily.saveBills(daily.bills().map { it.copy(reminderEnabled = false) })

        ReminderScheduler.cancel(this, "test_alarm")
        ReminderScheduler.scheduleAll(this, store)
        if (syncCloud && CloudSyncManager.isSignedIn()) CloudSyncManager.scheduleUpload(this)
    }

    private fun confirmResetGuide() {
        AlertDialog.Builder(this)
            .setTitle("Guide ডিফল্ট রিসেট")
            .setMessage("এই ফোনের Routine, খাবার, Alarm, হাজিরা, হিসাব, To-do, Medicine, Bill, নামাজ settings ও অন্যান্য local Guide data ডিফল্ট হবে। Firebase account logout হবে না এবং cloud backup delete হবে না।\n\nরিসেট করার আগে প্রয়োজন হলে Cloud Backup নিন।")
            .setPositiveButton("রিসেট করুন") { _, _ -> resetGuideToDefaults() }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun resetGuideToDefaults() {
        // Cancel schedules while their IDs are still available.
        disableAllGuideReminders(syncCloud = false)

        val prefs = getSharedPreferences("guide_store", MODE_PRIVATE)
        val profileName = prefs.getString("profile_name", "Guide User") ?: "Guide User"
        val pinHash = prefs.getString("pin_hash", null)
        val pinSalt = prefs.getString("pin_salt", null)

        prefs.edit().clear().apply()
        val editor = prefs.edit().putString("profile_name", profileName)
        if (!pinHash.isNullOrBlank()) editor.putString("pin_hash", pinHash)
        if (!pinSalt.isNullOrBlank()) editor.putString("pin_salt", pinSalt)
        editor.apply()
        getSharedPreferences("guide_ui", MODE_PRIVATE).edit().clear().apply()

        store = GuideStore(this)
        store.seedDefaultsIfNeeded()
        ReminderScheduler.scheduleAll(this, store)
        currentTab = "home"
        detailPage = null
        Toast.makeText(this, "Guide ডিফল্ট অবস্থায় রিসেট হয়েছে", Toast.LENGTH_LONG).show()
        render()
    }

'''
    s = s.replace(marker, helpers + marker, 1)
    p.write_text(s)
    print('v3.3 MainActivity reset/quick-off/pull-refresh patch applied')
else:
    print('v3.3 MainActivity patch already applied')

# Gradle dependency + real version bump.
g = Path('app/build.gradle.kts')
gs = g.read_text()
if 'swiperefreshlayout' not in gs:
    gs = req(gs, 'implementation("androidx.appcompat:appcompat:1.7.0")', 'implementation("androidx.appcompat:appcompat:1.7.0")\n    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")', 'swipe refresh dependency')
gs = gs.replace('versionCode = 14', 'versionCode = 16', 1)
gs = gs.replace('versionName = "3.1.0"', 'versionName = "3.3.0"', 1)
g.write_text(gs)
print('v3.3 Gradle dependency/version applied')

# Cloud metadata version label.
c = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = c.read_text()
cs = cs.replace('"appVersion" to "3.0.0"', '"appVersion" to "3.3.0"', 1)
c.write_text(cs)
print('v3.3 cloud metadata version applied')
