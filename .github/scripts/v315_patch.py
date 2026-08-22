from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# Guide v3.15
# - Add rainbow border to Water goal and Focus note dashboard cards.
# - Password eye show/hide control.
# - Keyboard Done submits login/register safely (avoids premature mid-password login).
# - Google Sign-In UI + Firebase credential flow, ready once Firebase Google provider
#   and SHA-1/OAuth client are configured and google-services.json is refreshed.
# ---------------------------------------------------------------------------

# MainActivity: finish the two dashboard cards that did not yet use v3.14 border.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideLoginUxGoogleV315' not in ms:
    ms = req(
        ms,
        '        val count = store.waterCount(); val c = card("#15213C"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }',
        '        val count = store.waterCount(); val c = card("#15213C").apply { applyAnimatedCardBorder(this, "#15213C", "#42D6FF") }; val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }',
        'water goal rainbow border'
    )
    ms = req(
        ms,
        '        val noteCard = card("#151F3B")',
        '        val noteCard = card("#151F3B").apply { applyAnimatedCardBorder(this, "#151F3B", "#B26CFF") }',
        'focus note rainbow border'
    )
    # Marker only; feature code lives in LoginActivity.
    marker = '    private fun buildTopBar(): View {'
    ms = req(ms, marker, '    // GuideLoginUxGoogleV315\n' + marker, 'v315 marker')
    mp.write_text(ms)
    print('v3.15 MainActivity missing dashboard borders applied')
else:
    print('v3.15 MainActivity patch already applied')

# LoginActivity.
lp = Path('app/src/main/java/com/guide/app/LoginActivity.kt')
ls = lp.read_text()

if 'GuideGoogleLoginV315' not in ls:
    # Imports.
    ls = req(
        ls,
        'import android.view.Gravity\nimport android.view.ViewGroup\n',
        'import android.view.Gravity\nimport android.view.KeyEvent\nimport android.view.ViewGroup\nimport android.view.inputmethod.EditorInfo\n',
        'login keyboard imports'
    )
    ls = req(
        ls,
        'import androidx.appcompat.app.AppCompatActivity\nimport com.google.firebase.auth.FirebaseAuth\n',
        'import androidx.appcompat.app.AppCompatActivity\nimport androidx.activity.result.contract.ActivityResultContracts\nimport com.google.android.gms.auth.api.signin.GoogleSignIn\nimport com.google.android.gms.auth.api.signin.GoogleSignInOptions\nimport com.google.android.gms.common.api.ApiException\nimport com.google.firebase.auth.FirebaseAuth\nimport com.google.firebase.auth.GoogleAuthProvider\n',
        'google auth imports'
    )

    # Google launcher after state fields. v3.0 has already added spinner state fields.
    anchor = '''    private var loadingText: TextView? = null\n'''
    launcher = r'''    private var loadingText: TextView? = null

    // GuideGoogleLoginV315
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            setBusyState(false)
            if (result.data != null) toast("Google Login সম্পন্ন হয়নি")
            return@registerForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                setBusyState(false)
                toast("Google Login token পাওয়া যায়নি • Firebase SHA-1 / OAuth setup দেখুন")
                return@registerForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    finishCloudLogin(account.email.orEmpty())
                }
                .addOnFailureListener {
                    setBusyState(false)
                    toast(firebaseError(it))
                }
        } catch (e: ApiException) {
            setBusyState(false)
            toast("Google Login ব্যর্থ হয়েছে • code ${e.statusCode}")
        }
    }
'''
    ls = req(ls, anchor, launcher, 'google launcher')

    # Password field is wrapped in a professional row with eye button.
    ls = req(
        ls,
        '''        card.addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))\n        card.addView(space(15))\n''',
        '''        card.addView(passwordFieldRow(password), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        card.addView(space(15))
''',
        'password eye row'
    )

    # Replace v3.0 submit block so keyboard Done uses exactly the same validation/action.
    old_submit = r'''        val actionButton = primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            if (busy) return@primaryButton
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (!validEmail(mail) || !validPassword(pass)) return@primaryButton
            if (registerMode) register(name?.text?.toString().orEmpty(), mail, pass)
            else login(mail, pass)
        }
        authActionButton = actionButton
        card.addView(actionButton)
'''
    new_submit = r'''        val submitAuth: () -> Unit = submit@{
            if (busy) return@submit
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (!validEmail(mail) || !validPassword(pass)) return@submit
            if (registerMode) register(name?.text?.toString().orEmpty(), mail, pass)
            else login(mail, pass)
        }

        // Safe auto-submit: typing alone never submits a partial password. Once the
        // user finishes the password and presses the keyboard Done/Enter action,
        // login starts immediately without needing to tap the button separately.
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        password.setOnEditorActionListener { _, actionId, event ->
            val done = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (done) {
                submitAuth()
                true
            } else false
        }

        val actionButton = primaryButton(if (registerMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") {
            submitAuth()
        }
        authActionButton = actionButton
        card.addView(actionButton)
'''
    ls = req(ls, old_submit, new_submit, 'shared submit and keyboard done')

    # Google button below spinner. It works for both new/existing Google users.
    google_ui_anchor = '''        loadingRow = busyLine\n        card.addView(busyLine)\n\n        if (!registerMode) {\n'''
    google_ui = '''        loadingRow = busyLine
        card.addView(busyLine)

        card.addView(space(8))
        card.addView(TextView(this).apply {
            text = "অথবা"
            textSize = 11.5f
            setTextColor(Color.parseColor("#7E8CB5"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(8))
        })
        card.addView(googleButton("G   Google দিয়ে চালিয়ে যান") {
            startGoogleLogin()
        })

        if (!registerMode) {
'''
    ls = req(ls, google_ui_anchor, google_ui, 'google login button')

    # Helpers before setBusyState (added by v3.0).
    helper_anchor = '''    private fun setBusyState(value: Boolean, message: String = "") {\n'''
    helpers = r'''    private fun passwordFieldRow(password: EditText): LinearLayout {
        // Remove the EditText's own rounded background and let the parent own the
        // surface so the eye button sits inside one clean responsive field.
        password.background = null
        password.setPadding(dp(18), 0, dp(8), 0)
        password.setSingleLine(true)
        password.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            password.setAutofillHints(android.view.View.AUTOFILL_HINT_PASSWORD)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded("#0F1730", 15)
            addView(password, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

            var visible = false
            addView(TextView(this@LoginActivity).apply {
                text = "👁"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#BCC7E8"))
                contentDescription = "Password দেখুন"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val cursor = password.selectionStart.coerceAtLeast(0)
                    visible = !visible
                    password.inputType = InputType.TYPE_CLASS_TEXT or if (visible) {
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    alpha = if (visible) 1f else 0.72f
                    contentDescription = if (visible) "Password লুকান" else "Password দেখুন"
                    password.setSelection(cursor.coerceAtMost(password.text.length))
                    password.requestFocus()
                }
            }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun googleButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#F5F7FF"))
        background = gradientCard("#253457", "#1A2747", 15)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun startGoogleLogin() {
        if (busy) return
        // google-services plugin creates default_web_client_id only after Firebase
        // Google provider/OAuth is configured and the refreshed JSON is installed.
        val clientIdRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (clientIdRes == 0) {
            toast("Google Login প্রস্তুত আছে • Firebase-এ Google provider + SHA-1 যোগ করে নতুন google-services.json দিন")
            return
        }
        val webClientId = getString(clientIdRes)
        if (webClientId.isBlank()) {
            toast("Firebase Web client ID পাওয়া যায়নি")
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        setBusyState(true, "Google Login খুলছে...")
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun finishCloudLogin(fallbackEmail: String) {
        CloudSyncManager.restoreLatest(this) { restored, message ->
            setBusyState(false)
            GuestSession.clear(this)
            store = GuideStore(this)
            val emailName = (auth.currentUser?.email ?: fallbackEmail).substringBefore("@").ifBlank { "Guide User" }
            val displayName = auth.currentUser?.displayName?.trim().orEmpty()
            val name = store.profileName().takeIf { it != "Guide User" }
                ?: displayName.takeIf { it.isNotBlank() }
                ?: emailName
            initializeLocalProfile(name)
            toast(message)
            if (!restored) CloudSyncManager.uploadNow(this)
            openGuide()
        }
    }

'''
    ls = req(ls, helper_anchor, helpers + helper_anchor, 'login helpers')

    lp.write_text(ls)
    print('v3.15 LoginActivity eye, Done-submit and Google login applied')
else:
    print('v3.15 LoginActivity patch already applied')

# Gradle dependency for Google Sign-In.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
if 'com.google.android.gms:play-services-auth' not in bs:
    bs = req(
        bs,
        '    implementation("com.google.firebase:firebase-auth")\n',
        '    implementation("com.google.firebase:firebase-auth")\n    implementation("com.google.android.gms:play-services-auth:21.3.0")\n',
        'play services auth dependency'
    )
bs = bs.replace('versionCode = 27', 'versionCode = 28', 1)
bs = bs.replace('versionName = "3.14.0"', 'versionName = "3.15.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.14.0"', '"appVersion" to "3.15.0"', 1)
cp.write_text(cs)
print('v3.15 version metadata applied')
