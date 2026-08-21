from pathlib import Path

# -------- LoginActivity --------
p = Path('app/src/main/java/com/guide/app/LoginActivity.kt')
s = p.read_text()

if 'এখন Skip করুন • Offline mode' not in s:
    def rep(old, new, name, count=1):
        global s
        if old not in s:
            raise SystemExit(f'pattern not found LoginActivity: {name}')
        s = s.replace(old, new, count)

    rep(
'''    private var registerMode = false
    private var busy = false
''',
'''    private var registerMode = false
    private var busy = false
    private var authActionButton: Button? = null
    private var loadingRow: LinearLayout? = null
    private var loadingText: TextView? = null
''',
'loading fields')

    old_button = '''        card.addView(primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            if (busy) return@primaryButton
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (!validEmail(mail) || !validPassword(pass)) return@primaryButton
            if (registerMode) register(name?.text?.toString().orEmpty(), mail, pass)
            else login(mail, pass)
        })
'''
    new_button = '''        val actionButton = primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            if (busy) return@primaryButton
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (!validEmail(mail) || !validPassword(pass)) return@primaryButton
            if (registerMode) register(name?.text?.toString().orEmpty(), mail, pass)
            else login(mail, pass)
        }
        authActionButton = actionButton
        card.addView(actionButton)

        val busyLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
            setPadding(0, dp(10), 0, dp(2))
        }
        busyLine.addView(android.widget.ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(28), dp(28)))
        busyLine.addView(TextView(this).apply {
            text = "লগইন হচ্ছে..."
            textSize = 12.5f
            setTextColor(Color.parseColor("#AEB9DA"))
            setPadding(dp(9), 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            loadingText = this
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)))
        loadingRow = busyLine
        card.addView(busyLine)
'''
    rep(old_button, new_button, 'auth button + spinner')

    rep(
'''        card.addView(space(8))
        card.addView(secondaryButton(if (registerMode) "আগে থেকেই account আছে? লগইন" else "নতুন? অ্যাকাউন্ট তৈরি করুন") {
            registerMode = !registerMode
            render()
        })
''',
'''        card.addView(space(8))
        card.addView(secondaryButton(if (registerMode) "আগে থেকেই account আছে? লগইন" else "নতুন? অ্যাকাউন্ট তৈরি করুন") {
            registerMode = !registerMode
            render()
        })
        card.addView(space(8))
        card.addView(secondaryButton("এখন Skip করুন • Offline mode") {
            if (busy) return@secondaryButton
            GuestSession.start(this)
            toast("Guest mode চালু হয়েছে • পরে account করলে cloud backup পাবেন")
            openGuide()
        })
''',
'skip button')

    # Replace busy assignments in async flows with visual state helpers.
    rep('''        busy = true
        auth.createUserWithEmailAndPassword(email, password)''', '''        setBusyState(true, "অ্যাকাউন্ট তৈরি হচ্ছে...")
        auth.createUserWithEmailAndPassword(email, password)''', 'register busy start')
    rep('''                busy = false
                initializeLocalProfile(cleanName)''', '''                setBusyState(false)
                GuestSession.clear(this)
                initializeLocalProfile(cleanName)''', 'register success')
    rep('''                busy = false
                toast(firebaseError(it))''', '''                setBusyState(false)
                toast(firebaseError(it))''', 'register failure', 1)

    rep('''        busy = true
        auth.signInWithEmailAndPassword(email, password)''', '''        setBusyState(true, "লগইন হচ্ছে...")
        auth.signInWithEmailAndPassword(email, password)''', 'login busy start')
    rep('''                    busy = false
                    store = GuideStore(this)''', '''                    setBusyState(false)
                    GuestSession.clear(this)
                    store = GuideStore(this)''', 'login success')
    # second matching failure belongs to login
    rep('''                busy = false
                toast(firebaseError(it))''', '''                setBusyState(false)
                toast(firebaseError(it))''', 'login failure', 1)

    rep('''        busy = true
        auth.sendPasswordResetEmail(email)''', '''        setBusyState(true, "Reset email পাঠানো হচ্ছে...")
        auth.useAppLanguage()
        auth.sendPasswordResetEmail(email)''', 'reset busy start')
    rep('''                busy = false
                AlertDialog.Builder(this)''', '''                setBusyState(false)
                AlertDialog.Builder(this)''', 'reset success')
    rep('''                    .setMessage("$email ঠিকানায় Firebase password reset link পাঠানো হয়েছে। Email খুলে নতুন password সেট করুন, তারপর Guide-এ ফিরে লগইন করুন।")''', '''                    .setMessage("$email ঠিকানায় password reset link পাঠানো হয়েছে। Inbox-এর পাশাপাশি Spam/Promotions-ও দেখুন। Email খুলে নতুন password সেট করে Guide-এ ফিরে লগইন করুন।")''', 'reset dialog message')
    rep('''                busy = false
                toast(firebaseError(it))''', '''                setBusyState(false)
                toast(firebaseError(it))''', 'reset failure', 1)

    marker = '''    private fun openGuide() {
'''
    helper = '''    private fun setBusyState(value: Boolean, message: String = "") {
        busy = value
        authActionButton?.isEnabled = !value
        authActionButton?.alpha = if (value) 0.72f else 1f
        loadingText?.text = message.ifBlank { "অপেক্ষা করুন..." }
        loadingRow?.visibility = if (value) android.view.View.VISIBLE else android.view.View.GONE
    }

'''
    if marker not in s:
        raise SystemExit('pattern not found LoginActivity: openGuide marker')
    s = s.replace(marker, helper + marker, 1)

    p.write_text(s)
    print('v3.0 LoginActivity spinner + guest patch applied')
else:
    print('v3.0 LoginActivity patch already applied')

# -------- MainActivity --------
p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()

if 'Guest mode • ক্লাউড ব্যাকআপ বন্ধ' not in s:
    def repm(old, new, name, count=1):
        global s
        if old not in s:
            raise SystemExit(f'pattern not found MainActivity: {name}')
        s = s.replace(old, new, count)

    repm(
'''        if (!store.hasProfile() || !CloudSyncManager.isSignedIn()) {''',
'''        if (!store.hasProfile() || (!CloudSyncManager.isSignedIn() && !GuestSession.isGuest(this))) {''',
'allow guest session')

    repm(
'''        val root = page()

        val welcomeBanner = LinearLayout(this).apply {''',
'''        val root = page()

        if (GuestSession.isGuest(this)) {
            val guestNotice = card("#352946").apply {
                background = roundedStroke("#352946", "#B89BFF", 1, 18)
            }
            guestNotice.addView(text("Guest mode • ক্লাউড ব্যাকআপ বন্ধ", 15f, "#FFFFFF", bold = true))
            guestNotice.addView(text("আপনার ডাটা শুধু এই ফোনে থাকবে। Email account তৈরি/লগইন করলে Firebase backup, restore ও password recovery চালু হবে।", 12.5f, "#D6C9EF").apply { setPadding(0, dp(6), 0, 0) })
            guestNotice.addView(text("অ্যাকাউন্ট তৈরি / লগইন করুন  →", 13f, "#CDBDFF", bold = true).apply { setPadding(0, dp(11), 0, 0) })
            guestNotice.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            root.addView(guestNotice)
            root.addView(space(12))
        }

        val welcomeBanner = LinearLayout(this).apply {''',
'guest dashboard notice')

    repm(
'''        val lockCard = card("#261D36", padding = 12).apply {''',
'''        val guestMode = GuestSession.isGuest(this)
        val lockCard = card("#261D36", padding = 12).apply {''',
'guest drawer state')
    repm('''        lockLabels.addView(text("অ্যাকাউন্ট থেকে লগআউট", 15f, "#FFFFFF", bold = true))
        lockLabels.addView(text("Email/Password দিয়ে আবার লগইন করুন", 11f, "#B19BC8"))''', '''        lockLabels.addView(text(if (guestMode) "অ্যাকাউন্ট তৈরি / লগইন" else "অ্যাকাউন্ট থেকে লগআউট", 15f, "#FFFFFF", bold = true))
        lockLabels.addView(text(if (guestMode) "ক্লাউড backup ও recovery চালু করুন" else "Email/Password দিয়ে আবার লগইন করুন", 11f, "#B19BC8"))''', 'guest drawer labels')
    repm('''        lockCard.setOnClickListener { lockApp() }''', '''        lockCard.setOnClickListener {
            if (guestMode) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else lockApp()
        }''', 'guest drawer action')

    repm(
'''    private fun lockApp() {
        CloudSyncManager.deleteSession()
        startActivity(Intent(this, LoginActivity::class.java))''',
'''    private fun lockApp() {
        GuestSession.clear(this)
        CloudSyncManager.deleteSession()
        startActivity(Intent(this, LoginActivity::class.java))''',
'clear guest on logout')

    p.write_text(s)
    print('v3.0 MainActivity guest patch applied')
else:
    print('v3.0 MainActivity patch already applied')
