from pathlib import Path

p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()

if 'Email/Password দিয়ে আবার লগইন করুন' in s:
    print('v2.7 MainActivity patch already applied')
    raise SystemExit(0)

def rep(old, new, name, count=1):
    global s
    if old not in s:
        raise SystemExit(f'pattern not found: {name}')
    s = s.replace(old, new, count)

rep(
'''        store = GuideStore(this)
        if (!store.hasProfile()) {''',
'''        store = GuideStore(this)
        if (!store.hasProfile() || !CloudSyncManager.isSignedIn()) {''',
'firebase session gate'
)

rep(
'''        drawer.addView(ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            addView(menu)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))''',
'''        drawer.addView(ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isScrollbarFadingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(menu)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))''',
'hide drawer scrollbar'
)

rep(
'''        val lockCard = card("#1A2444", padding = 12).apply {
            background = roundedStroke("#1A2444", "#70FFFFFF", 1, 16)
        }
        val lockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lockRow.addView(text("⌁", 21f, "#FFB5B9", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val lockLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        lockLabels.addView(text("Guide লক করুন", 15f, "#FFFFFF", bold = true))
        lockLabels.addView(text("আবার PIN লাগবে", 11f, "#7F8EB7"))
        lockRow.addView(lockLabels)
        lockCard.addView(lockRow)
        lockCard.setOnClickListener { lockApp() }
        drawer.addView(lockCard)''',
'''        val lockCard = card("#261D36", padding = 12).apply {
            background = roundedStroke("#261D36", "#A078FF", 1, 16)
        }
        val lockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lockRow.addView(text("↪", 21f, "#FFB5C8", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val lockLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        lockLabels.addView(text("অ্যাকাউন্ট থেকে লগআউট", 15f, "#FFFFFF", bold = true))
        lockLabels.addView(text("Email/Password দিয়ে আবার লগইন করুন", 11f, "#B19BC8"))
        lockRow.addView(lockLabels)
        lockCard.addView(lockRow)
        lockCard.setOnClickListener { lockApp() }
        drawer.addView(lockCard)''',
'logout card'
)

old_drawer = '''    private fun drawerItem(icon: String, label: String, active: Boolean, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = roundedStroke(if (active) "#26335B" else "#121C38", "#72FFFFFF", 1, 15)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5); bottomMargin = dp(5)
            }
            setOnClickListener { action() }
            addView(text(icon, 18f, if (active) "#C6BCFF" else "#9CA9CF", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(text(label, 14f, if (active) "#FFFFFF" else "#C5CDEA", bold = active).apply { setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (active) addView(text("•", 18f, "#8D79FF", bold = true))
        }
    }'''

new_drawer = '''    private fun drawerItem(icon: String, label: String, active: Boolean, action: () -> Unit): LinearLayout {
        val fill = when {
            label.startsWith("ড্যাশবোর্ড") -> "#273A68"
            label.startsWith("দৈনিক পরিকল্পনা") -> "#21445E"
            label.startsWith("রুটিন") -> "#3B315F"
            label.startsWith("খাবারের") -> "#174B49"
            label.startsWith("অ্যালার্ম") -> "#2D4568"
            label.startsWith("নামাজের") -> "#244C44"
            label.startsWith("কোর্স") -> "#554226"
            label.startsWith("হাজিরা") -> "#3A3E69"
            label.startsWith("হিসাব") -> "#264D5C"
            label.startsWith("ট্র্যাকিং") -> "#3C355B"
            label.startsWith("সেটিংস") -> "#37405D"
            label.startsWith("ব্যাকআপ") -> "#4A3557"
            label.startsWith("PDF") -> "#3D446A"
            else -> "#202C4D"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = roundedStroke(fill, if (active) "#D4CCFF" else "#66FFFFFF", if (active) 2 else 1, 16)
            elevation = dp(if (active) 4 else 2).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6); bottomMargin = dp(6)
            }
            setOnClickListener { action() }
            addView(text(icon, 18f, if (active) "#FFFFFF" else "#D5DCF5", bold = true).apply {
                gravity = Gravity.CENTER
                background = rounded(if (active) "#6553D9" else "#243254", 12)
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(text(label, 14f, "#FFFFFF", bold = active).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (active) addView(text("●", 12f, "#C6BCFF", bold = true))
        }
    }'''
rep(old_drawer, new_drawer, 'colorful drawer items')

rep(
'''        root.addView(space(9)); root.addView(rowCard("⌁", "Change PIN", "Update your 4-digit access PIN", "#3E6EA8") { changePin() })''',
'''        root.addView(space(9)); root.addView(rowCard("✉", "Email account", CloudSyncManager.currentEmail().ifBlank { "লগইন প্রয়োজন" }, "#3E6EA8") { lockApp() })''',
'remove change pin card'
)

rep(
'''    private fun lockApp() { startActivity(Intent(this, LoginActivity::class.java)); finish() }''',
'''    private fun lockApp() {
        CloudSyncManager.deleteSession()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }''',
'logout action'
)

p.write_text(s)
print('v2.7 MainActivity patch applied')
