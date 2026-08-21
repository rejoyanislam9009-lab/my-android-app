from pathlib import Path

p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()

if 'GUIDE DAILY • আপনার দিনের স্মার্ট সঙ্গী' in s:
    print('v2.8 dashboard patch already applied')
    raise SystemExit(0)

old = '''    private fun buildHomePage(): LinearLayout {
        val root = page()
        val today = store.today()
'''
new = '''    private fun buildHomePage(): LinearLayout {
        val root = page()

        val welcomeBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#6D4CFF"),
                    Color.parseColor("#536DFE"),
                    Color.parseColor("#1D8DB8")
                )
            ).apply { cornerRadius = dp(24).toFloat() }
            elevation = dp(8).toFloat()
        }
        val bannerTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bannerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bannerText.addView(text("GUIDE DAILY • আপনার দিনের স্মার্ট সঙ্গী", 10.5f, "#E8E6FF", bold = true).apply { letterSpacing = 0.08f })
        bannerText.addView(text("আজকের দিনটা আরও সুন্দর করুন", 23f, "#FFFFFF", bold = true).apply { setPadding(0, dp(6), 0, 0) })
        bannerText.addView(text("${store.profileName()}, আপনার রুটিন, নামাজ, পানি, হিসাব ও লক্ষ্য—সব এক জায়গায় গুছিয়ে রাখুন।", 13f, "#E1E8FF").apply { setPadding(0, dp(7), 0, 0) })
        bannerTop.addView(bannerText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bannerTop.addView(text("G", 22f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded("#32FFFFFF", 18)
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        welcomeBanner.addView(bannerTop)
        welcomeBanner.addView(space(15))
        val bannerBottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bannerBottom.addView(statusPill(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), "#2EFFFFFF", "#FFFFFF"))
        bannerBottom.addView(hSpace(8))
        bannerBottom.addView(statusPill("NEW • DAILY GUIDE", "#2EFFFFFF", "#FFFFFF"))
        welcomeBanner.addView(bannerBottom)
        root.addView(welcomeBanner)
        root.addView(space(10))

        val marqueeBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = roundedStroke("#141E39", "#4D7A8CFF", 1, 14)
        }
        marqueeBox.addView(text("✦", 16f, "#AFA7FF", bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(26), dp(30)))
        marqueeBox.addView(TextView(this).apply {
            text = "জীবনকে সুন্দর, গোছানো ও সময়মতো করতে প্রতিদিন Guide ব্যবহার করুন  •  ছোট ছোট ভালো অভ্যাসই বড় পরিবর্তন আনে  •  আজকের কাজ আজই সম্পন্ন করুন  •  নিজের লক্ষ্যকে প্রতিদিন একটু একটু করে এগিয়ে নিন"
            textSize = 12.5f
            setTextColor(Color.parseColor("#D3DBF7"))
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            isFocusable = true
            isFocusableInTouchMode = true
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        root.addView(marqueeBox)
        root.addView(space(18))

        val today = store.today()
'''
if old not in s:
    raise SystemExit('pattern not found: buildHomePage start')
s = s.replace(old, new, 1)
p.write_text(s)
print('v2.8 dashboard banner + marquee patch applied')
