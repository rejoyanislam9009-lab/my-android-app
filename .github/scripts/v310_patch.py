from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)


# ---------------------------------------------------------------------------
# Guide v3.10
# 1) Ringtone/adhan preview volume keys control MEDIA volume only.
# 2) Dashboard hero becomes an illustrated, auto-sliding image carousel.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideBannerCarouselV310' not in ms:
    # While MainActivity is visible, hardware volume buttons should control the
    # media stream used by ringtone previews. AlarmActivity keeps its own
    # explicit hardware-key stop behavior for a real firing alarm.
    ms = req(
        ms,
        '        super.onCreate(savedInstanceState)\n        store = GuideStore(this)',
        '        super.onCreate(savedInstanceState)\n        // GuideBannerCarouselV310: preview audio follows the normal media volume.\n        volumeControlStream = android.media.AudioManager.STREAM_MUSIC\n        store = GuideStore(this)',
        'media volume control stream'
    )

    # Replace the old single gradient welcome card created by v2.8/v3.9 with
    # an image-style ViewFlipper carousel. Keep the marquee below it.
    start_token = '        val welcomeBanner = LinearLayout(this).apply {'
    end_token = '        root.addView(space(10))\n'
    start = ms.find(start_token)
    if start < 0:
        raise SystemExit('pattern not found: welcome banner start')
    end = ms.find(end_token, start)
    if end < 0:
        raise SystemExit('pattern not found: welcome banner end')
    end += len(end_token)
    ms = ms[:start] + '''        root.addView(buildImageBannerCarousel())
        root.addView(space(12))
''' + ms[end:]

    marker = '    private fun buildHomePage(): LinearLayout {\n'
    if marker not in ms:
        raise SystemExit('pattern not found: buildHomePage marker')

    helpers = r'''    private fun buildImageBannerCarousel(): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val flipper = android.widget.ViewFlipper(this).apply {
            isAutoStart = false
            setInAnimation(android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_PARENT, 1f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f
            ).apply {
                duration = 460L
                interpolator = android.view.animation.DecelerateInterpolator()
            })
            setOutAnimation(android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, -1f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0f
            ).apply {
                duration = 460L
                interpolator = android.view.animation.AccelerateInterpolator()
            })
        }

        flipper.addView(imageBannerSlide(
            kicker = "GUIDE DAILY • 01/03",
            titleText = "আজকের দিনটা আরও সুন্দর করুন",
            subtitle = "${store.profileName()}, রুটিন, কাজ ও লক্ষ্য—সব এক জায়গায় গুছিয়ে এগিয়ে যান।",
            badge = "আজকের পরিকল্পনা",
            drawableRes = R.drawable.guide_banner_focus,
            colors = intArrayOf(Color.parseColor("#7758FF"), Color.parseColor("#496BFF"), Color.parseColor("#1591C5"))
        ))
        flipper.addView(imageBannerSlide(
            kicker = "PRAYER & TIME • 02/03",
            titleText = "নামাজ ও সময়—এক জায়গায়",
            subtitle = "পাঁচ ওয়াক্তের সময়, কাউন্টডাউন ও আজান রিমাইন্ডার সহজে দেখুন।",
            badge = "নামাজের সময়সূচি",
            drawableRes = R.drawable.guide_banner_prayer,
            colors = intArrayOf(Color.parseColor("#146A72"), Color.parseColor("#1B7F75"), Color.parseColor("#285A89"))
        ))
        flipper.addView(imageBannerSlide(
            kicker = "DAILY TRACK • 03/03",
            titleText = "নিজেকে প্রতিদিন ট্র্যাক করুন",
            subtitle = "পানি, হাজিরা, হিসাব, ওষুধ ও অভ্যাস—ছোট অগ্রগতিও চোখের সামনে রাখুন।",
            badge = "দৈনন্দিন জীবন",
            drawableRes = R.drawable.guide_banner_wellness,
            colors = intArrayOf(Color.parseColor("#7B3F83"), Color.parseColor("#C34F70"), Color.parseColor("#D77A4D"))
        ))

        wrapper.addView(flipper, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(214)
        ))

        val hint = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, 0)
        }
        hint.addView(text("●", 8f, "#A99CFF"))
        hint.addView(text("  ●  ", 8f, "#59698E"))
        hint.addView(text("●", 8f, "#59698E"))
        hint.addView(text("   অটো স্লাইড", 10.5f, "#7180A7", bold = true))
        wrapper.addView(hint)

        val autoSlide = object : Runnable {
            override fun run() {
                if (!flipper.isAttachedToWindow) return
                flipper.showNext()
                handler.postDelayed(this, 5200L)
            }
        }
        handler.postDelayed(autoSlide, 5200L)
        return wrapper
    }

    private fun imageBannerSlide(
        kicker: String,
        titleText: String,
        subtitle: String,
        badge: String,
        drawableRes: Int,
        colors: IntArray
    ): android.widget.FrameLayout {
        return android.widget.FrameLayout(this).apply {
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
                setPadding(dp(21), dp(20), dp(126), dp(18))
            }
            copy.addView(text(kicker, 10.5f, "#E9ECFF", bold = true).apply {
                letterSpacing = 0.07f
            })
            copy.addView(text(titleText, 23f, "#FFFFFF", bold = true).apply {
                setPadding(0, dp(7), 0, 0)
                maxLines = 2
            })
            copy.addView(text(subtitle, 12.5f, "#EDF1FF").apply {
                setPadding(0, dp(8), 0, 0)
                maxLines = 3
            })
            copy.addView(text(badge, 10.5f, "#FFFFFF", bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = roundedStroke("#29FFFFFF", "#55FFFFFF", 1, 13)
                setPadding(dp(12), dp(7), dp(12), dp(7))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(11)
            })
            addView(copy, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

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
    ms = ms.replace(marker, helpers + marker, 1)
    mp.write_text(ms)
    print('v3.10 MainActivity media-volume + image carousel applied')
else:
    print('v3.10 MainActivity patch already applied')


# ---------------------------------------------------------------------------
# Lightweight vector illustrations bundled in the APK. No network is needed
# to display the dashboard image banners.
# ---------------------------------------------------------------------------
drawable_dir = Path('app/src/main/res/drawable')
drawable_dir.mkdir(parents=True, exist_ok=True)

(drawable_dir / 'guide_banner_focus.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="160dp" android:height="180dp"
    android:viewportWidth="160" android:viewportHeight="180">
    <path android:fillColor="#28FFFFFF" android:pathData="M80,10A70,70 0,1 0,80 150A70,70 0,1 0,80 10"/>
    <path android:fillColor="#35FFFFFF" android:pathData="M38,40L118,40Q128,40 128,50L128,132Q128,142 118,142L38,142Q28,142 28,132L28,50Q28,40 38,40Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M49,62L58,71L74,52L81,58L59,84L42,69Z"/>
    <path android:fillColor="#DFFFFFFF" android:pathData="M86,61L116,61L116,68L86,68ZM86,82L116,82L116,89L86,89ZM48,103L116,103L116,110L48,110ZM48,121L101,121L101,128L48,128Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M111,18L114,28L124,31L114,34L111,44L108,34L98,31L108,28Z"/>
    <path android:fillColor="#CCFFFFFF" android:pathData="M29,19L31,26L38,28L31,30L29,37L27,30L20,28L27,26Z"/>
</vector>
''')

(drawable_dir / 'guide_banner_prayer.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="160dp" android:height="180dp"
    android:viewportWidth="160" android:viewportHeight="180">
    <path android:fillColor="#27FFFFFF" android:pathData="M80,10A70,70 0,1 0,80 150A70,70 0,1 0,80 10"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M111,27A21,21 0,1 0,125 62A25,25 0,1 1,111 27Z"/>
    <path android:fillColor="#EFFFFFFF" android:pathData="M23,126L137,126L137,139L23,139Z"/>
    <path android:fillColor="#E8FFFFFF" android:pathData="M43,84Q58,61 73,84L73,126L43,126Z"/>
    <path android:fillColor="#F8FFFFFF" android:pathData="M77,75Q97,48 117,75L117,126L77,126Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M31,56L39,56L39,126L31,126ZM121,51L129,51L129,126L121,126Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M28,56L35,41L42,56ZM118,51L125,34L132,51Z"/>
    <path android:fillColor="#45FFFFFF" android:pathData="M19,145L141,145L141,151L19,151Z"/>
</vector>
''')

(drawable_dir / 'guide_banner_wellness.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="160dp" android:height="180dp"
    android:viewportWidth="160" android:viewportHeight="180">
    <path android:fillColor="#25FFFFFF" android:pathData="M80,10A70,70 0,1 0,80 150A70,70 0,1 0,80 10"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M48,42C48,42 28,67 28,82A20,20 0,0 0,68 82C68,67 48,42 48,42Z"/>
    <path android:fillColor="#F5FFFFFF" android:pathData="M100,47C89,35 67,44 67,62C67,81 100,99 100,99C100,99 133,81 133,62C133,44 111,35 100,47Z"/>
    <path android:fillColor="#DFFFFFFF" android:pathData="M33,118L55,118L55,139L33,139ZM69,105L91,105L91,139L69,139ZM105,91L127,91L127,139L105,139Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M111,24L114,34L124,37L114,40L111,50L108,40L98,37L108,34Z"/>
</vector>
''')
print('v3.10 bundled vector banner illustrations written')


# ---------------------------------------------------------------------------
# Version metadata.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 22', 'versionCode = 23', 1)
bs = bs.replace('versionName = "3.9.0"', 'versionName = "3.10.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.9.0"', '"appVersion" to "3.10.0"', 1)
cp.write_text(cs)
print('v3.10 version metadata applied')
