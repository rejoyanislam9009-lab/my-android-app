from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideBannerCarouselV311' not in ms:
    start = ms.find('    private fun buildImageBannerCarousel(): LinearLayout {')
    end = ms.find('    private fun buildHomePage(): LinearLayout {', start)
    if start < 0 or end < 0:
        raise SystemExit('v3.10 carousel block not found')

    carousel = r'''    // GuideBannerCarouselV311: six interactive slides, swipe/tap navigation and synced dots.
    private fun buildImageBannerCarousel(): LinearLayout {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val flipper = android.widget.ViewFlipper(this).apply { isAutoStart = false }
        val dotViews = mutableListOf<TextView>()
        var touchDownX = 0f
        var touching = false

        fun setDirection(forward: Boolean) {
            val fromIn = if (forward) 1f else -1f
            val toOut = if (forward) -1f else 1f
            flipper.inAnimation = android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_PARENT, fromIn,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f
            ).apply {
                duration = 390L
                interpolator = android.view.animation.DecelerateInterpolator()
            }
            flipper.outAnimation = android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, toOut,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f
            ).apply {
                duration = 390L
                interpolator = android.view.animation.AccelerateInterpolator()
            }
        }

        fun updateDots() {
            val active = flipper.displayedChild.coerceIn(0, dotViews.lastIndex.coerceAtLeast(0))
            dotViews.forEachIndexed { index, dot ->
                dot.setTextColor(Color.parseColor(if (index == active) "#C8BFFF" else "#52617F"))
                dot.textSize = if (index == active) 10f else 8f
                dot.alpha = if (index == active) 1f else 0.82f
            }
        }

        val slides = listOf(
            imageBannerSlide(
                kicker = "GUIDE DAILY • 01/06",
                titleText = "আজকের দিনটা আরও সুন্দর করুন",
                subtitle = "${store.profileName()}, রুটিন, কাজ ও লক্ষ্য—সব এক জায়গায় গুছিয়ে এগিয়ে যান।",
                badge = "আজকের পরিকল্পনা",
                drawableRes = R.drawable.guide_banner_focus,
                colors = intArrayOf(Color.parseColor("#7758FF"), Color.parseColor("#496BFF"), Color.parseColor("#1591C5")),
                action = { navigate("plan") }
            ),
            imageBannerSlide(
                kicker = "PRAYER & TIME • 02/06",
                titleText = "নামাজ ও সময়—এক জায়গায়",
                subtitle = "পাঁচ ওয়াক্তের সময়, কাউন্টডাউন ও আজান রিমাইন্ডার সহজে দেখুন।",
                badge = "নামাজের সময়সূচি",
                drawableRes = R.drawable.guide_banner_prayer,
                colors = intArrayOf(Color.parseColor("#146A72"), Color.parseColor("#1B7F75"), Color.parseColor("#285A89")),
                action = { navigate("plan", "prayer") }
            ),
            imageBannerSlide(
                kicker = "DAILY TRACK • 03/06",
                titleText = "নিজেকে প্রতিদিন ট্র্যাক করুন",
                subtitle = "পানি, হাজিরা, হিসাব ও অগ্রগতি—সবকিছু এক নজরে দেখুন।",
                badge = "ট্র্যাকিং সারাংশ",
                drawableRes = R.drawable.guide_banner_wellness,
                colors = intArrayOf(Color.parseColor("#7B3F83"), Color.parseColor("#C34F70"), Color.parseColor("#D77A4D")),
                action = { navigate("track") }
            ),
            imageBannerSlide(
                kicker = "MEDICINE CARE • 04/06",
                titleText = "ওষুধের সময় আর ভুলবেন না",
                subtitle = "ওষুধ, dose, সময় ও ঐচ্ছিক রিমাইন্ডার—স্বাস্থ্য রুটিন গুছিয়ে রাখুন।",
                badge = "ওষুধের রিমাইন্ডার",
                drawableRes = R.drawable.guide_banner_medicine,
                colors = intArrayOf(Color.parseColor("#8F315C"), Color.parseColor("#C54872"), Color.parseColor("#E16D63")),
                action = { navigate("plan", "medicine") }
            ),
            imageBannerSlide(
                kicker = "WORK & INCOME • 05/06",
                titleText = "কাজের সময় ও আয় হিসাব রাখুন",
                subtitle = "হাজিরা, কাজের ঘণ্টা, ঘণ্টাপ্রতি রিয়াল ও মোট আয় সহজে ট্র্যাক করুন।",
                badge = "হাজিরা ও আয়",
                drawableRes = R.drawable.guide_banner_income,
                colors = intArrayOf(Color.parseColor("#155E66"), Color.parseColor("#237F73"), Color.parseColor("#5E8B54")),
                action = { navigate("track", "attendance") }
            ),
            imageBannerSlide(
                kicker = "GOOD HABITS • 06/06",
                titleText = "ছোট অভ্যাসে বড় পরিবর্তন আনুন",
                subtitle = "প্রতিদিনের ভালো অভ্যাসে টিক দিন, streak বাড়ান এবং ধারাবাহিকতা ধরে রাখুন।",
                badge = "অভ্যাস ট্র্যাকার",
                drawableRes = R.drawable.guide_banner_habit,
                colors = intArrayOf(Color.parseColor("#4D3A93"), Color.parseColor("#7056C8"), Color.parseColor("#B05FAD")),
                action = { navigate("track", "habits") }
            )
        )
        slides.forEach { flipper.addView(it) }
        setDirection(true)

        wrapper.addView(flipper, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(214)))

        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, dp(1))
        }
        repeat(slides.size) { index ->
            val dot = text("●", if (index == 0) 10f else 8f, if (index == 0) "#C8BFFF" else "#52617F").apply {
                gravity = Gravity.CENTER
                setPadding(dp(5), 0, dp(5), 0)
                isClickable = true
                setOnClickListener {
                    if (index == flipper.displayedChild) return@setOnClickListener
                    val forward = index > flipper.displayedChild
                    setDirection(forward)
                    flipper.displayedChild = index
                    updateDots()
                }
            }
            dotViews.add(dot)
            dots.addView(dot)
        }
        wrapper.addView(dots)

        lateinit var autoSlide: Runnable
        fun restartAuto() {
            handler.removeCallbacks(autoSlide)
            handler.postDelayed(autoSlide, 5600L)
        }
        autoSlide = object : Runnable {
            override fun run() {
                if (!flipper.isAttachedToWindow) return
                if (!touching) {
                    setDirection(true)
                    flipper.showNext()
                    updateDots()
                }
                handler.postDelayed(this, 5600L)
            }
        }

        flipper.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touching = true
                    handler.removeCallbacks(autoSlide)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchDownX
                    touching = false
                    when {
                        dx <= -dp(42) -> {
                            setDirection(true)
                            flipper.showNext()
                            updateDots()
                        }
                        dx >= dp(42) -> {
                            setDirection(false)
                            flipper.showPrevious()
                            updateDots()
                        }
                        kotlin.math.abs(dx) < dp(14) -> flipper.currentView?.performClick()
                    }
                    restartAuto()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    restartAuto()
                    true
                }
                else -> true
            }
        }
        updateDots()
        handler.postDelayed(autoSlide, 5600L)
        return wrapper
    }

    private fun imageBannerSlide(
        kicker: String,
        titleText: String,
        subtitle: String,
        badge: String,
        drawableRes: Int,
        colors: IntArray,
        action: () -> Unit
    ): android.widget.FrameLayout {
        return android.widget.FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = dp(25).toFloat()
                setStroke(dp(1), Color.parseColor("#66FFFFFF"))
            }
            elevation = dp(15).toFloat()
            translationZ = dp(3).toFloat()
            clipToPadding = false

            val copy = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(21), dp(18), dp(126), dp(17))
            }
            copy.addView(text(kicker, 10.5f, "#F0F2FF", bold = true).apply { letterSpacing = 0.07f })
            copy.addView(text(titleText, 22.5f, "#FFFFFF", bold = true).apply {
                setPadding(0, dp(6), 0, 0)
                maxLines = 2
            })
            copy.addView(text(subtitle, 12.1f, "#F2F5FF").apply {
                setPadding(0, dp(7), 0, 0)
                maxLines = 3
            })
            copy.addView(text("$badge  ›", 10.5f, "#FFFFFF", bold = true).apply {
                gravity = Gravity.CENTER
                background = roundedStroke("#29FFFFFF", "#68FFFFFF", 1, 13)
                setPadding(dp(12), dp(7), dp(12), dp(7))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
            addView(copy, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val art = android.widget.ImageView(this@MainActivity).apply {
                setImageResource(drawableRes)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                alpha = 0.98f
                elevation = dp(6).toFloat()
                contentDescription = titleText
            }
            addView(art, android.widget.FrameLayout.LayoutParams(dp(124), dp(158), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(8)
            })
        }
    }

'''
    ms = ms[:start] + carousel + ms[end:]

    old_section = '''    private fun sectionTitle(value: String) = text(value, 13f, "#B9C5EA", bold = true).apply { setPadding(dp(2), 0, 0, dp(9)); letterSpacing = 0.045f }
'''
    new_section = '''    private fun sectionTitle(value: String) = text(value, 13f, "#B9C5EA", bold = true).apply {
        setPadding(dp(2), 0, 0, dp(9))
        letterSpacing = 0.045f
        applyHeadingShine(this)
    }

    private fun applyHeadingShine(target: TextView) {
        val animator = android.animation.ValueAnimator.ofObject(
            android.animation.ArgbEvaluator(),
            Color.parseColor("#93A4D5"),
            Color.parseColor("#F7F9FF"),
            Color.parseColor("#AAB8E4")
        ).apply {
            duration = 2600L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener { target.setTextColor(it.animatedValue as Int) }
        }
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { if (!animator.isStarted) animator.start() }
            override fun onViewDetachedFromWindow(v: View) { animator.cancel() }
        })
        target.post { if (target.isAttachedToWindow && !animator.isStarted) animator.start() }
    }
'''
    ms = req(ms, old_section, new_section, 'section heading shine')

    mp.write_text(ms)
    print('v3.11 interactive six-slide carousel + heading shine applied')
else:
    print('v3.11 MainActivity patch already applied')

# Three additional bundled illustrations.
d = Path('app/src/main/res/drawable')
d.mkdir(parents=True, exist_ok=True)

(d / 'guide_banner_medicine.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="160dp" android:height="180dp" android:viewportWidth="160" android:viewportHeight="180">
    <path android:fillColor="#24FFFFFF" android:pathData="M80,10A70,70 0,1 0,80 150A70,70 0,1 0,80 10"/>
    <path android:fillColor="#F8FFFFFF" android:pathData="M49,48L105,104Q116,115 105,126Q94,137 83,126L27,70Q16,59 27,48Q38,37 49,48Z"/>
    <path android:fillColor="#7AFFFFFF" android:pathData="M43,82L82,43L94,55L55,94Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M103,34L111,34L111,53L130,53L130,61L111,61L111,80L103,80L103,61L84,61L84,53L103,53Z"/>
    <path android:fillColor="#44FFFFFF" android:pathData="M30,137L129,137L129,145L30,145Z"/>
</vector>''')

(d / 'guide_banner_income.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="160dp" android:height="180dp" android:viewportWidth="160" android:viewportHeight="180">
    <path android:fillColor="#24FFFFFF" android:pathData="M80,10A70,70 0,1 0,80 150A70,70 0,1 0,80 10"/>
    <path android:fillColor="#EFFFFFFF" android:pathData="M28,61L132,61Q139,61 139,68L139,127Q139,134 132,134L28,134Q21,134 21,127L21,68Q21,61 28,61Z"/>
    <path android:fillColor="#48FFFFFF" android:pathData="M21,78L139,78L139,94L21,94Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M91,106A17,17 0,1 0,125 106A17,17 0,1 0,91 106"/>
    <path android:fillColor="#4A7D75" android:pathData="M105,96L112,96L112,116L105,116ZM99,103L118,103L118,109L99,109Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M34,35L44,35L44,54L34,54ZM55,28L65,28L65,54L55,54ZM76,20L86,20L86,54L76,54Z"/>
</vector>''')

(d / 'guide_banner_habit.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="160dp" android:height="180dp" android:viewportWidth="160" android:viewportHeight="180">
    <path android:fillColor="#24FFFFFF" android:pathData="M80,10A70,70 0,1 0,80 150A70,70 0,1 0,80 10"/>
    <path android:fillColor="#F5FFFFFF" android:pathData="M38,42L122,42Q131,42 131,51L131,132Q131,141 122,141L38,141Q29,141 29,132L29,51Q29,42 38,42Z"/>
    <path android:fillColor="#7159B8" android:pathData="M46,67L55,76L72,56L79,63L56,90L39,74Z"/>
    <path android:fillColor="#7159B8" android:pathData="M46,106L55,115L72,95L79,102L56,129L39,113Z"/>
    <path android:fillColor="#7A7159B8" android:pathData="M87,66L117,66L117,73L87,73ZM87,105L117,105L117,112L87,112Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M111,20L114,30L124,33L114,36L111,46L108,36L98,33L108,30Z"/>
</vector>''')
print('v3.11 extra banner illustrations written')

bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 23', 'versionCode = 24', 1)
bs = bs.replace('versionName = "3.10.0"', 'versionName = "3.11.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.10.0"', '"appVersion" to "3.11.0"', 1)
cp.write_text(cs)
print('v3.11 version metadata applied')
