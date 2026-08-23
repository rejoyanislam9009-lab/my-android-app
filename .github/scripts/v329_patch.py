from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# ---------------------------------------------------------------------------
# Guide v3.29
# - Individual PDF share buttons inside sidebar History for every archived bill.
# - Premium success/error/info feedback for the most important finance actions.
# - Stronger same-page scroll restoration to stop the visible jump shown in the
#   device video after routine/meal/payment actions rebuild the screen.
# - Dashboard "active functions" summary.
# - Friendly no-internet feedback for explicit Firebase/cloud actions.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideUxV329' not in ms:
    # 1) Stronger scroll-position restore. Some device layouts finish measuring
    # after the first posted scroll, which could leave the page at the top.
    old_restore = '''        if (previousPageKey == pageKey && previousScrollY > 0) {
            guideContentScroll?.post {
                guideContentScroll?.scrollTo(0, previousScrollY)
            }
        }'''
    new_restore = '''        if (previousPageKey == pageKey && previousScrollY > 0) {
            val restoreY = previousScrollY
            guideContentScroll?.post {
                guideContentScroll?.scrollTo(0, restoreY)
                guideContentScroll?.postDelayed({ guideContentScroll?.scrollTo(0, restoreY) }, 90L)
                guideContentScroll?.postDelayed({ guideContentScroll?.scrollTo(0, restoreY) }, 240L)
            }
        }'''
    require(ms, old_restore, 'v3.25 scroll restoration')
    ms = ms.replace(old_restore, new_restore, 1)

    # 2) Sidebar History: each archived expense gets its own PDF/WhatsApp button,
    # in addition to the existing whole-month PDF.
    history_item_anchor = '''                monthCard.addView(itemBox)
                monthCard.addView(space(7))'''
    history_item_new = '''                itemBox.addView(space(8))
                itemBox.addView(pillButton("📄 এই হিসাব আলাদা PDF", "#28765E") {
                    dialog.dismiss()
                    shareRoomExpensePdfV328(expense)
                })
                monthCard.addView(itemBox)
                monthCard.addView(space(7))'''
    require(ms, history_item_anchor, 'history expense card append')
    ms = ms.replace(history_item_anchor, history_item_new, 1)

    # 3) Replace finance-specific plain Toasts with premium Guide feedback.
    ms = ms.replace(
        'Toast.makeText(this, "✓ History-তে সংরক্ষণ হয়েছে", Toast.LENGTH_LONG).show()',
        'GuideUiFeedback.success(this, "এই হিসাবটি নিরাপদে History-তে সংরক্ষণ হয়েছে।", "History-তে চলে গেছে")',
    )
    ms = ms.replace(
        'Toast.makeText(this, "Pending ${moneyText(due)} আছে—সব payment complete হলে Clear করা যাবে", Toast.LENGTH_LONG).show()',
        'GuideUiFeedback.warning(this, "এখনও ${moneyText(due)} বাকি আছে। সব payment complete হলে Clear → History করা যাবে।", "Payment বাকি আছে")',
    )
    ms = ms.replace(
        'Toast.makeText(this, "PDF তৈরি/শেয়ার করা যায়নি", Toast.LENGTH_LONG).show()',
        'GuideUiFeedback.error(this, "এই হিসাবের PDF তৈরি বা শেয়ার করা যায়নি। আবার চেষ্টা করুন।", "PDF সমস্যা")',
    )
    ms = ms.replace(
        'startActivity(android.content.Intent.createChooser(send, "PDF শেয়ার করুন"))',
        'GuideUiFeedback.info(this, "PDF প্রস্তুত হয়েছে। WhatsApp বা অন্য অ্যাপ নির্বাচন করুন।", "এই হিসাবের PDF প্রস্তুত")\n            startActivity(android.content.Intent.createChooser(send, "PDF শেয়ার করুন"))',
        1,
    )
    ms = ms.replace(
        'Toast.makeText(this, "History PDF share করা যায়নি: ${it.message ?: \\"unknown error\\"}", Toast.LENGTH_LONG).show()',
        'GuideUiFeedback.error(this, "History PDF তৈরি বা শেয়ার করা যায়নি। আবার চেষ্টা করুন।", "PDF সমস্যা")',
    )

    # Payment completion/partial-payment messages produced by v3.28.
    ms = ms.replace(
        'Toast.makeText(this, "✓ ${member.name} • ${moneyText(due)} PAYMENT COMPLETE", Toast.LENGTH_LONG).show()',
        'GuideUiFeedback.success(this, "${member.name} • ${moneyText(due)} সম্পূর্ণ payment হয়েছে।", "Payment Complete")',
    )

    # 4) Show enabled/active features on Dashboard.
    dash_anchor = '''        root.addView(hero)
        root.addView(space(18))

        root.addView(sectionTitle("আজকের নামাজের সময় • NEW"))'''
    dash_new = '''        root.addView(hero)
        root.addView(space(18))

        // GuideUxV329 • what the user turns on is visible from Dashboard.
        root.addView(sectionTitle("চালু ফাংশন"))
        val dashActiveRoutines = store.routines().count { it.alarmEnabled }
        val dashActiveMeals = store.meals().count { it.alarmEnabled }
        val dashActiveAlarms = store.alarms().count { it.enabled }
        val dashPrayer = store.prayerSettings()
        val dashFinance = FinanceStore(this)
        val dashArchived = dashFinance.archivedExpenseIdsV328()
        val dashPendingBills = dashFinance.roomExpenses().count {
            it.amount > 0.005 && it.id !in dashArchived && dashFinance.expenseCollection(it).third > 0.005
        }
        val activeCard = card("#152641")
        activeCard.addView(text("যা এখন চালু আছে", 15f, "#FFFFFF", bold = true))
        activeCard.addView(text("Dashboard থেকেই active reminder ও চলমান হিসাব এক নজরে দেখুন।", 10.8f, "#8F9FC3").apply { setPadding(0, dp(3), 0, dp(9)) })
        fun dashActiveLine(icon: String, label: String, value: String, active: Boolean) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(icon, 15f, if (active) "#75D8B5" else "#647492", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), dp(30)))
            row.addView(text(label, 12.5f, if (active) "#D9E2F6" else "#7886A5", bold = active), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text(value, 11.5f, if (active) "#75D8B5" else "#697898", bold = true))
            activeCard.addView(row)
        }
        dashActiveLine("✓", "Routine reminder", "$dashActiveRoutines চালু", dashActiveRoutines > 0)
        dashActiveLine("🍽", "Meal reminder", "$dashActiveMeals চালু", dashActiveMeals > 0)
        dashActiveLine("⏰", "Alarm", "$dashActiveAlarms চালু", dashActiveAlarms > 0)
        dashActiveLine("☪", "নামাজ reminder", if (dashPrayer.enabled) "চালু" else "বন্ধ", dashPrayer.enabled)
        dashActiveLine("▣", "চলমান Room/Mess হিসাব", "$dashPendingBills টি", dashPendingBills > 0)
        root.addView(activeCard)
        root.addView(space(18))

        root.addView(sectionTitle("আজকের নামাজের সময় • NEW"))'''
    require(ms, dash_anchor, 'dashboard prayer anchor')
    ms = ms.replace(dash_anchor, dash_new, 1)

    # 5) Routine/meal actions now show a contextual premium confirmation. This
    # also makes it obvious which action just changed before the screen rebuilds.
    routine_save = 'store.saveRoutines(items); ReminderScheduler.scheduleAll(this, store); render()'
    if routine_save in ms:
        ms = ms.replace(
            routine_save,
            'store.saveRoutines(items); ReminderScheduler.scheduleAll(this, store); GuideUiFeedback.success(this, when (which) { 0 -> if (done) "রুটিনটি আবার Pending করা হয়েছে।" else "রুটিনটি Done হয়েছে।"; 2 -> if (item.alarmEnabled) "রুটিন reminder বন্ধ হয়েছে।" else "রুটিন reminder চালু হয়েছে।"; else -> "রুটিন আপডেট হয়েছে।" }, "রুটিন আপডেট"); render()',
            1,
        )
    meal_save = 'store.saveMeals(items); ReminderScheduler.scheduleAll(this, store); render()'
    if meal_save in ms:
        ms = ms.replace(
            meal_save,
            'store.saveMeals(items); ReminderScheduler.scheduleAll(this, store); GuideUiFeedback.success(this, when (which) { 0 -> if (done) "Meal status আপডেট হয়েছে।" else "Meal complete হয়েছে।"; 2 -> if (item.alarmEnabled) "Meal reminder বন্ধ হয়েছে।" else "Meal reminder চালু হয়েছে।"; else -> "Meal আপডেট হয়েছে।" }, "Meal আপডেট"); render()',
            1,
        )

    mp.write_text(ms)
    print('v3.29 MainActivity UX/history/dashboard patch applied')
else:
    print('v3.29 MainActivity patch already applied')


# ---------------------------------------------------------------------------
# Login: prevent confusing Firebase attempts when there is no internet.
# ---------------------------------------------------------------------------
lp = Path('app/src/main/java/com/guide/app/LoginActivity.kt')
ls = lp.read_text()
if 'GuideOfflineUxV329' not in ls:
    # Register/login primary action.
    auth_anchor = '''        card.addView(primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            if (busy) return@primaryButton'''
    auth_new = '''        card.addView(primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            if (busy) return@primaryButton
            if (!GuideUiFeedback.requireInternet(this, if (registerMode) "অ্যাকাউন্ট তৈরি" else "লগইন")) return@primaryButton'''
    require(ls, auth_anchor, 'login/register primary')
    ls = ls.replace(auth_anchor, auth_new, 1)

    reset_anchor = '''            card.addView(secondaryButton("পাসওয়ার্ড ভুলে গেছেন? Reset করুন") {
                val mail = email.text.toString().trim()'''
    reset_new = '''            card.addView(secondaryButton("পাসওয়ার্ড ভুলে গেছেন? Reset করুন") {
                if (!GuideUiFeedback.requireInternet(this, "Password reset")) return@secondaryButton
                val mail = email.text.toString().trim()'''
    require(ls, reset_anchor, 'login password reset')
    ls = ls.replace(reset_anchor, reset_new, 1)

    restore_anchor = '''        card.addView(primaryButton("ক্লাউড রিস্টোর করে চালু করুন") {
            if (busy) return@primaryButton'''
    restore_new = '''        card.addView(primaryButton("ক্লাউড রিস্টোর করে চালু করুন") {
            if (busy) return@primaryButton
            if (!GuideUiFeedback.requireInternet(this, "Cloud restore")) return@primaryButton'''
    require(ls, restore_anchor, 'signed in cloud restore')
    ls = ls.replace(restore_anchor, restore_new, 1)

    # Use premium feedback for ordinary auth errors/messages too.
    old_toast = '    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()'
    require(ls, old_toast, 'login toast helper')
    ls = ls.replace(old_toast, '    private fun toast(message: String) = GuideUiFeedback.info(this, message, "Guide Account")', 1)
    ls += '\n// GuideOfflineUxV329\n'
    lp.write_text(ls)
    print('v3.29 Login offline feedback applied')
else:
    print('v3.29 Login patch already applied')


# ---------------------------------------------------------------------------
# Backup: local backup remains fully offline; only Firebase/reset actions require
# a validated connection and show a clear premium notification when unavailable.
# ---------------------------------------------------------------------------
bp = Path('app/src/main/java/com/guide/app/BackupActivity.kt')
bs = bp.read_text()
if 'GuideOfflineBackupV329' not in bs:
    upload_anchor = '''            firebase.addView(button("এখনই ক্লাউড ব্যাকআপ নিন", "#5C4EE0") {
                CloudSyncManager.uploadNow(this) { _, message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            })'''
    upload_new = '''            firebase.addView(button("এখনই ক্লাউড ব্যাকআপ নিন", "#5C4EE0") {
                if (!GuideUiFeedback.requireInternet(this, "Cloud backup")) return@button
                CloudSyncManager.uploadNow(this) { ok, message ->
                    if (ok) GuideUiFeedback.success(this, message, "Cloud Backup") else GuideUiFeedback.error(this, message, "Cloud Backup")
                }
            })'''
    require(bs, upload_anchor, 'backup cloud upload')
    bs = bs.replace(upload_anchor, upload_new, 1)

    restore_button_anchor = '''            firebase.addView(button("ক্লাউড থেকে সর্বশেষ ডাটা রিস্টোর", "#267B68") {
                AlertDialog.Builder(this)'''
    restore_button_new = '''            firebase.addView(button("ক্লাউড থেকে সর্বশেষ ডাটা রিস্টোর", "#267B68") {
                if (!GuideUiFeedback.requireInternet(this, "Cloud restore")) return@button
                AlertDialog.Builder(this)'''
    require(bs, restore_button_anchor, 'backup cloud restore')
    bs = bs.replace(restore_button_anchor, restore_button_new, 1)

    reset_button_anchor = '''            firebase.addView(button("Password reset email পাঠান", "#38527C") {
                val email = CloudSyncManager.currentEmail()'''
    reset_button_new = '''            firebase.addView(button("Password reset email পাঠান", "#38527C") {
                if (!GuideUiFeedback.requireInternet(this, "Password reset email")) return@button
                val email = CloudSyncManager.currentEmail()'''
    require(bs, reset_button_anchor, 'backup password reset')
    bs = bs.replace(reset_button_anchor, reset_button_new, 1)

    bs = bs.replace(
        'Toast.makeText(this, "ব্যাকআপ আপডেট হয়েছে", Toast.LENGTH_SHORT).show()',
        'GuideUiFeedback.success(this, "লোকাল ব্যাকআপ আপডেট হয়েছে। ইন্টারনেট ছাড়াও এই কপি ব্যবহার করা যাবে।", "Local Backup")',
    )
    bs += '\n// GuideOfflineBackupV329\n'
    bp.write_text(bs)
    print('v3.29 Backup offline feedback applied')
else:
    print('v3.29 Backup patch already applied')


# ---------------------------------------------------------------------------
# Cloud manager: explicit calls receive an immediate offline callback instead of
# waiting for Firebase to fail. Automatic background schedule remains silent.
# ---------------------------------------------------------------------------
cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
if 'GuideCloudOfflineV329' not in cs:
    upload_sig = '''    fun uploadNow(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser'''
    upload_sig_new = '''    fun uploadNow(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!GuideUiFeedback.isOnline(context)) {
            onComplete?.invoke(false, "ইন্টারনেট সংযোগ নেই—Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন")
            return
        }
        val user = FirebaseAuth.getInstance().currentUser'''
    require(cs, upload_sig, 'Cloud uploadNow')
    cs = cs.replace(upload_sig, upload_sig_new, 1)

    restore_sig = '''    fun restoreLatest(context: Context, onComplete: (Boolean, String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser'''
    restore_sig_new = '''    fun restoreLatest(context: Context, onComplete: (Boolean, String) -> Unit) {
        if (!GuideUiFeedback.isOnline(context)) {
            onComplete(false, "ইন্টারনেট সংযোগ নেই—Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন")
            return
        }
        val user = FirebaseAuth.getInstance().currentUser'''
    require(cs, restore_sig, 'Cloud restoreLatest')
    cs = cs.replace(restore_sig, restore_sig_new, 1)
    cs += '\n// GuideCloudOfflineV329\n'
    cp.write_text(cs)
    print('v3.29 Cloud offline callback applied')
else:
    print('v3.29 Cloud patch already applied')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 42', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.29.0"', gs, count=1)
gp.write_text(gs)

# Cloud metadata version string after prior patches.
cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
cs = cs.replace('3.28.0', '3.29.0')
cp.write_text(cs)
print('v3.29 version metadata applied')
