from pathlib import Path
import re


def replace_if(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        print(f'v3.29 recover: optional pattern not found: {name}')
        return text
    return text.replace(old, new, count)


# MainActivity was written before the original v329 script encountered the
# Google-login-era LoginActivity shape. Keep it as-is and continue the remaining
# v3.29 work here.

lp = Path('app/src/main/java/com/guide/app/LoginActivity.kt')
ls = lp.read_text()
if 'GuideOfflineUxV329' not in ls:
    # v3.15+ uses a shared submitAuth lambda for button + keyboard Done.
    submit_anchor = '''        val submitAuth: () -> Unit = submit@{
            if (busy) return@submit'''
    submit_new = '''        val submitAuth: () -> Unit = submit@{
            if (busy) return@submit
            if (!GuideUiFeedback.requireInternet(this, if (registerMode) "অ্যাকাউন্ট তৈরি" else "লগইন")) return@submit'''
    ls = replace_if(ls, submit_anchor, submit_new, 'shared login submit')

    google_anchor = '''    private fun startGoogleLogin() {
        if (busy) return'''
    google_new = '''    private fun startGoogleLogin() {
        if (busy) return
        if (!GuideUiFeedback.requireInternet(this, "Google Login")) return'''
    ls = replace_if(ls, google_anchor, google_new, 'Google login network guard')

    reset_anchor = '''            card.addView(secondaryButton("পাসওয়ার্ড ভুলে গেছেন? Reset করুন") {
                val mail = email.text.toString().trim()'''
    reset_new = '''            card.addView(secondaryButton("পাসওয়ার্ড ভুলে গেছেন? Reset করুন") {
                if (!GuideUiFeedback.requireInternet(this, "Password reset")) return@secondaryButton
                val mail = email.text.toString().trim()'''
    ls = replace_if(ls, reset_anchor, reset_new, 'password reset network guard')

    restore_anchor = '''        card.addView(primaryButton("ক্লাউড রিস্টোর করে চালু করুন") {
            if (busy) return@primaryButton'''
    restore_new = '''        card.addView(primaryButton("ক্লাউড রিস্টোর করে চালু করুন") {
            if (busy) return@primaryButton
            if (!GuideUiFeedback.requireInternet(this, "Cloud restore")) return@primaryButton'''
    ls = replace_if(ls, restore_anchor, restore_new, 'cloud restore network guard')

    old_toast = '    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()'
    if old_toast in ls:
        ls = ls.replace(old_toast, '    private fun toast(message: String) = GuideUiFeedback.info(this, message, "Guide Account")', 1)

    ls += '\n// GuideOfflineUxV329\n'
    lp.write_text(ls)
    print('v3.29 recover: Login offline feedback applied')
else:
    print('v3.29 recover: Login already patched')


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
    bs = replace_if(bs, upload_anchor, upload_new, 'Backup cloud upload')

    restore_anchor = '''            firebase.addView(button("ক্লাউড থেকে সর্বশেষ ডাটা রিস্টোর", "#267B68") {
                AlertDialog.Builder(this)'''
    restore_new = '''            firebase.addView(button("ক্লাউড থেকে সর্বশেষ ডাটা রিস্টোর", "#267B68") {
                if (!GuideUiFeedback.requireInternet(this, "Cloud restore")) return@button
                AlertDialog.Builder(this)'''
    bs = replace_if(bs, restore_anchor, restore_new, 'Backup cloud restore')

    reset_anchor = '''            firebase.addView(button("Password reset email পাঠান", "#38527C") {
                val email = CloudSyncManager.currentEmail()'''
    reset_new = '''            firebase.addView(button("Password reset email পাঠান", "#38527C") {
                if (!GuideUiFeedback.requireInternet(this, "Password reset email")) return@button
                val email = CloudSyncManager.currentEmail()'''
    bs = replace_if(bs, reset_anchor, reset_new, 'Backup password reset')

    bs = bs.replace(
        'Toast.makeText(this, "ব্যাকআপ আপডেট হয়েছে", Toast.LENGTH_SHORT).show()',
        'GuideUiFeedback.success(this, "লোকাল ব্যাকআপ আপডেট হয়েছে। ইন্টারনেট ছাড়াও এই কপি ব্যবহার করা যাবে।", "Local Backup")'
    )
    bs += '\n// GuideOfflineBackupV329\n'
    bp.write_text(bs)
    print('v3.29 recover: Backup offline feedback applied')
else:
    print('v3.29 recover: Backup already patched')


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
    cs = replace_if(cs, upload_sig, upload_sig_new, 'Cloud uploadNow')

    restore_sig = '''    fun restoreLatest(context: Context, onComplete: (Boolean, String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser'''
    restore_sig_new = '''    fun restoreLatest(context: Context, onComplete: (Boolean, String) -> Unit) {
        if (!GuideUiFeedback.isOnline(context)) {
            onComplete(false, "ইন্টারনেট সংযোগ নেই—Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন")
            return
        }
        val user = FirebaseAuth.getInstance().currentUser'''
    cs = replace_if(cs, restore_sig, restore_sig_new, 'Cloud restoreLatest')
    cs += '\n// GuideCloudOfflineV329\n'

cs = cs.replace('3.28.0', '3.29.0')
cp.write_text(cs)
print('v3.29 recover: Cloud offline/version applied')


gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 42', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.29.0"', gs, count=1)
gp.write_text(gs)
print('v3.29 recover: version metadata applied')
