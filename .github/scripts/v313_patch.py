from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# Guide v3.13
# - Responsive premium sidebar with compact two-column groups.
# - Animated colorful borders on the dashboard cards users see most often.
# - Decorative lightweight dashboard background.
# - Stronger left/right manual banner swipe while preserving vertical scroll.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideDashboardPremiumV313' not in ms:
    # Dashboard background view is inserted behind the transparent dashboard shell.
    ms = req(
        ms,
        '''        val frame = FrameLayout(this).apply { background = gradient("#080D1A", "#111A35") }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#080D1A", "#111A35")
        }''',
        '''        val isDashboard = currentTab == "home" && detailPage == null
        val frame = FrameLayout(this).apply { background = gradient("#080D1A", "#111A35") }
        if (isDashboard) {
            frame.addView(
                GuideDashboardBackdropView(this),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (isDashboard) android.graphics.drawable.ColorDrawable(Color.TRANSPARENT) else gradient("#080D1A", "#111A35")
        }''',
        'dashboard decorative background'
    )

    # Responsive sidebar panel width instead of a hard-coded 302dp.
    ms = req(
        ms,
        '        frame.addView(drawer, FrameLayout.LayoutParams(dp(302), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))',
        '        frame.addView(drawer, FrameLayout.LayoutParams(drawerWidthPx(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))',
        'responsive drawer width'
    )

    # Premium sidebar surface / spacing.
    ms = req(
        ms,
        '''            setPadding(dp(18), dp(22), dp(18), dp(14))
            background = gradient("#121C38", "#0B1227")''',
        '''            setPadding(dp(14), dp(18), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#142445"), Color.parseColor("#101B36"), Color.parseColor("#091126"))
            )''',
        'premium drawer surface'
    )

    ms = req(
        ms,
        '            background = rounded("#7257FF", 20)',
        '''            background = premiumGradientStroke("#6350E8", "#B99BFF", 1, 20)
            elevation = dp(10).toFloat()
            translationZ = dp(2).toFloat()''',
        'premium drawer brand icon'
    )

    ms = req(
        ms,
        '''        brandText.addView(text("Guide", 22f, "#FFFFFF", bold = true))
        brandText.addView(text(store.profileName(), 12f, "#8E9BC3"))''',
        '''        brandText.addView(text("Guide", 21f, "#FFFFFF", bold = true))
        brandText.addView(text(store.profileName(), 12f, "#B2BCE0"))
        brandText.addView(text("SMART DAILY ASSISTANT", 8.5f, "#7487B7", bold = true).apply { letterSpacing = 0.10f })''',
        'drawer brand copy'
    )

    # Compact paired layout for the most-used sidebar actions.
    old_plan = '''        menu.addView(drawerItem("▤", "দৈনিক পরিকল্পনা", currentTab == "plan" && detailPage == null) { navigate("plan") })
        menu.addView(space(10))

        menu.addView(drawerSection("পরিকল্পনা"))
        menu.addView(drawerItem("✓", "রুটিন", detailPage == "routines") { navigate("plan", "routines") })
        menu.addView(drawerItem("🍽", "খাবারের রুটিন", detailPage == "meals") { navigate("plan", "meals") })
        menu.addView(drawerItem("⏰", "অ্যালার্ম", detailPage == "alarms") { navigate("plan", "alarms") })'''
    new_plan = '''        menu.addView(space(8))
        menu.addView(drawerSection("পরিকল্পনা"))
        menu.addView(drawerPair(
            drawerItem("▤", "দৈনিক পরিকল্পনা", currentTab == "plan" && detailPage == null) { navigate("plan") },
            drawerItem("✓", "রুটিন", detailPage == "routines") { navigate("plan", "routines") }
        ))
        menu.addView(drawerPair(
            drawerItem("🍽", "খাবারের রুটিন", detailPage == "meals") { navigate("plan", "meals") },
            drawerItem("⏰", "অ্যালার্ম", detailPage == "alarms") { navigate("plan", "alarms") }
        ))'''
    ms = req(ms, old_plan, new_plan, 'paired planning drawer')

    old_track = '''        menu.addView(drawerItem("◎", "হাজিরা", detailPage == "attendance") { navigate("track", "attendance") })
        menu.addView(drawerItem("▣", "হিসাব", detailPage == "money") { navigate("track", "money") })
        menu.addView(drawerItem("◉", "ট্র্যাকিং সারাংশ", currentTab == "track" && detailPage == null) { navigate("track") })'''
    new_track = '''        menu.addView(drawerPair(
            drawerItem("◎", "হাজিরা", detailPage == "attendance") { navigate("track", "attendance") },
            drawerItem("▣", "হিসাব", detailPage == "money") { navigate("track", "money") }
        ))
        menu.addView(drawerItem("◉", "ট্র্যাকিং সারাংশ", currentTab == "track" && detailPage == null) { navigate("track") })'''
    ms = req(ms, old_track, new_track, 'paired tracking drawer')

    old_life = '''        menu.addView(drawerItem("☑", "করণীয় কাজ • NEW", detailPage == "todos") { navigate("plan", "todos") })
        menu.addView(drawerItem("★", "অভ্যাস ট্র্যাকার • NEW", detailPage == "habits") { navigate("track", "habits") })
        menu.addView(drawerItem("✚", "ওষুধের রিমাইন্ডার • NEW", detailPage == "medicine") { navigate("plan", "medicine") })
        menu.addView(drawerItem("▦", "বিল রিমাইন্ডার • NEW", detailPage == "bills") { navigate("track", "bills") })
        menu.addView(drawerItem("◫", "সাপ্তাহিক রিপোর্ট • NEW", detailPage == "weekly") { navigate("track", "weekly") })'''
    new_life = '''        menu.addView(drawerPair(
            drawerItem("☑", "করণীয় কাজ • NEW", detailPage == "todos") { navigate("plan", "todos") },
            drawerItem("★", "অভ্যাস ট্র্যাকার • NEW", detailPage == "habits") { navigate("track", "habits") }
        ))
        menu.addView(drawerPair(
            drawerItem("✚", "ওষুধের রিমাইন্ডার • NEW", detailPage == "medicine") { navigate("plan", "medicine") },
            drawerItem("▦", "বিল রিমাইন্ডার • NEW", detailPage == "bills") { navigate("track", "bills") }
        ))
        menu.addView(drawerItem("◫", "সাপ্তাহিক রিপোর্ট • NEW", detailPage == "weekly") { navigate("track", "weekly") })'''
    ms = req(ms, old_life, new_life, 'paired daily-life drawer')

    # Replace drawer section/item helpers as a single stable block. This keeps
    # all menu destinations but makes both full-width and paired items responsive.
    helper_start = ms.find('    private fun drawerSection(label: String)')
    helper_end = ms.find('    private fun openDrawer()', helper_start)
    if helper_start < 0 or helper_end < 0:
        raise SystemExit('pattern not found: drawer helper block')
    drawer_helpers = r'''    // GuideDashboardPremiumV313
    private fun drawerSection(label: String) = text(label, 10.5f, "#8294C2", bold = true).apply {
        setPadding(dp(7), dp(7), 0, dp(5))
        letterSpacing = 0.055f
    }

    private fun drawerPair(left: LinearLayout, right: LinearLayout): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4); topMargin = dp(4); bottomMargin = dp(4)
            })
            addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4); topMargin = dp(4); bottomMargin = dp(4)
            })
        }
    }

    private fun drawerItem(icon: String, label: String, active: Boolean, action: () -> Unit): LinearLayout {
        val fill = when {
            label.startsWith("ড্যাশবোর্ড") -> "#243F72"
            label.startsWith("দৈনিক পরিকল্পনা") -> "#1B536B"
            label.startsWith("রুটিন") -> "#4A3671"
            label.startsWith("খাবারের") -> "#2A623D"
            label.startsWith("অ্যালার্ম") -> "#6C314A"
            label.startsWith("নামাজের") -> "#276A67"
            label.startsWith("কোর্স") -> "#6C5527"
            label.startsWith("হাজিরা") -> "#3F437E"
            label.startsWith("হিসাব") -> "#246377"
            label.startsWith("ট্র্যাকিং") -> "#4A3A70"
            label.startsWith("করণীয়") -> "#573A70"
            label.startsWith("অভ্যাস") -> "#226456"
            label.startsWith("ওষুধ") -> "#733D58"
            label.startsWith("বিল") -> "#6A5124"
            label.startsWith("সাপ্তাহিক") -> "#34577C"
            label.startsWith("সেটিংস") -> "#3E4B70"
            label.startsWith("ব্যাকআপ") -> "#603D67"
            label.startsWith("PDF") -> "#454F7A"
            else -> "#24304F"
        }
        val stroke = if (active) "#DDD6FF" else "#52FFFFFF"
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = premiumGradientStroke(fill, stroke, if (active) 2 else 1, 16)
            elevation = dp(if (active) 10 else 5).toFloat()
            translationZ = dp(if (active) 2 else 1).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4); bottomMargin = dp(4)
            }
            setOnClickListener { action() }
            applyDepthPress(if (active) 9 else 5)
            addView(text(icon, 17f, "#FFFFFF", bold = true).apply {
                gravity = Gravity.CENTER
                background = premiumGradientStroke(if (active) "#765DFF" else shadeHex(fill, 1.12f), "#4FFFFFFF", 1, 11)
                elevation = dp(4).toFloat()
            }, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(text(label, if (label.length > 16) 11.5f else 12.5f, "#FFFFFF", bold = active).apply {
                setPadding(dp(8), 0, dp(3), 0)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (active) addView(text("●", 10f, "#C9C1FF", bold = true))
        }
    }

    private fun shadeHex(hex: String, factor: Float): String {
        val c = Color.parseColor(hex)
        val r = (Color.red(c) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * factor).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun drawerWidthPx(): Int {
        val screen = resources.displayMetrics.widthPixels
        return minOf(dp(334), (screen * 0.90f).toInt())
    }

'''
    ms = ms[:helper_start] + drawer_helpers + ms[helper_end:]

    ms = ms.replace('panel.translationX = -dp(302).toFloat()', 'panel.translationX = -drawerWidthPx().toFloat()', 1)
    ms = ms.replace('panel.animate().translationX(-dp(302).toFloat())', 'panel.animate().translationX(-drawerWidthPx().toFloat())', 1)

    # Animated accent borders for the visible dashboard card builders.
    animator_helper = r'''    private fun applyAnimatedCardBorder(target: View, fill: String, accent: String, radiusDp: Int = 20) {
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(shadeColor(fill, 1.18f), Color.parseColor(fill), shadeColor(fill, 0.70f))
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(accent))
        }
        target.background = bg
        target.elevation = dp(8).toFloat()
        val evaluator = android.animation.ArgbEvaluator()
        val c1 = Color.parseColor(accent)
        val c2 = Color.parseColor("#9A7CFF")
        val c3 = Color.parseColor("#49D4C5")
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener {
                val p = it.animatedFraction * 3f
                val color = when {
                    p < 1f -> evaluator.evaluate(p, c1, c2) as Int
                    p < 2f -> evaluator.evaluate(p - 1f, c2, c3) as Int
                    else -> evaluator.evaluate(p - 2f, c3, c1) as Int
                }
                bg.setStroke(dp(if (p in 0.85f..1.25f) 2 else 1), color)
            }
        }
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { if (!animator.isStarted) animator.start() }
            override fun onViewDetachedFromWindow(v: View) { animator.cancel() }
        })
        target.post { if (target.isAttachedToWindow && !animator.isStarted) animator.start() }
    }

'''
    marker = '    private fun formBox() = LinearLayout(this).apply'
    if marker not in ms:
        raise SystemExit('pattern not found: formBox helper marker')
    ms = ms.replace(marker, animator_helper + marker, 1)

    # rowCard drives Next reminder, routines, Accounts and many useful dashboard rows.
    ms = req(
        ms,
        '        val c = card("#17213E"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }',
        '        val c = card("#17213E").apply { applyAnimatedCardBorder(this, "#17213E", accent) }; val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }',
        'animated row cards'
    )

    # v3.4 expanded compactAction onto multiple lines; patch only its card creation.
    compact_start = ms.find('    private fun compactAction(')
    compact_end = ms.find('    private fun simpleLine(', compact_start)
    if compact_start < 0 or compact_end < 0:
        raise SystemExit('pattern not found: compact action block')
    compact_block = ms[compact_start:compact_end]
    compact_block_new = compact_block.replace(
        'val c = card("#17213E").apply {',
        'val c = card("#17213E").apply { applyAnimatedCardBorder(this, "#17213E", accent);',
        1
    )
    if compact_block_new == compact_block:
        raise SystemExit('pattern not found: compact action card creation')
    ms = ms[:compact_start] + compact_block_new + ms[compact_end:]

    ms = ms.replace(
        '        val lifeCard = card("#14243A")',
        '        val lifeCard = card("#14243A").apply { applyAnimatedCardBorder(this, "#14243A", "#4ECDB6") }',
        1
    )

    # Pause auto-slide as soon as the finger lands; restart after gesture/tap.
    swipe_anchor = '''        flipper.onSwipeRight = {
            setDirection(false)
            flipper.showPrevious()
            updateDots()
            restartAuto()
        }
'''
    swipe_new = '''        flipper.onSwipeRight = {
            setDirection(false)
            flipper.showPrevious()
            updateDots()
            restartAuto()
        }
        flipper.onGestureStart = { handler.removeCallbacks(autoSlide) }
        flipper.onGestureEnd = { restartAuto() }
'''
    ms = req(ms, swipe_anchor, swipe_new, 'banner gesture pause/resume')

    mp.write_text(ms)
    print('v3.13 MainActivity premium responsive dashboard/sidebar applied')
else:
    print('v3.13 MainActivity patch already applied')

# ---------------------------------------------------------------------------
# Strong bidirectional banner gesture handling. dispatchTouchEvent sees every
# event before clickable child views, so both left and right swipes are equal.
# Vertical motion is still passed to the containing dashboard ScrollView.
# ---------------------------------------------------------------------------
swipe_file = Path('app/src/main/java/com/guide/app/SwipeBannerFlipper.kt')
swipe_file.write_text(r'''package com.guide.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ViewFlipper
import kotlin.math.abs

class SwipeBannerFlipper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onGestureStart: (() -> Unit)? = null
    var onGestureEnd: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var horizontalGesture = false
    private var childCancelled = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                horizontalGesture = false
                childCancelled = false
                onGestureStart?.invoke()
                return super.dispatchTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!horizontalGesture && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.08f) {
                    horizontalGesture = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (!childCancelled) {
                        childCancelled = true
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                return if (horizontalGesture) true else super.dispatchTouchEvent(event)
            }

            MotionEvent.ACTION_UP -> {
                if (horizontalGesture) {
                    val dx = event.x - downX
                    val minDistance = (32f * resources.displayMetrics.density).coerceAtLeast(touchSlop * 1.8f)
                    when {
                        dx <= -minDistance -> onSwipeLeft?.invoke()
                        dx >= minDistance -> onSwipeRight?.invoke()
                    }
                    horizontalGesture = false
                    childCancelled = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onGestureEnd?.invoke()
                    return true
                }
                val handled = super.dispatchTouchEvent(event)
                parent?.requestDisallowInterceptTouchEvent(false)
                onGestureEnd?.invoke()
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                horizontalGesture = false
                childCancelled = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onGestureEnd?.invoke()
                return super.dispatchTouchEvent(event)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
''')
print('v3.13 bidirectional SwipeBannerFlipper written')

# ---------------------------------------------------------------------------
# Lightweight responsive dashboard background. Pure Canvas drawing means no
# extra image assets, no network calls, and it scales cleanly to all screens.
# ---------------------------------------------------------------------------
backdrop = Path('app/src/main/java/com/guide/app/GuideDashboardBackdropView.kt')
backdrop.write_text(r'''package com.guide.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

class GuideDashboardBackdropView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#07101F"),
                Color.parseColor("#0B1730"),
                Color.parseColor("#111C3C"),
                Color.parseColor("#081329")
            ),
            floatArrayOf(0f, 0.34f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        paint.color = Color.parseColor("#183E78")
        paint.alpha = 72
        canvas.drawCircle(width * 0.10f, height * 0.18f, width * 0.36f, paint)

        paint.color = Color.parseColor("#5D3F8E")
        paint.alpha = 46
        canvas.drawCircle(width * 0.94f, height * 0.42f, width * 0.42f, paint)

        paint.color = Color.parseColor("#186C69")
        paint.alpha = 34
        canvas.drawCircle(width * 0.28f, height * 0.82f, width * 0.30f, paint)

        line.color = Color.parseColor("#7D8EEA")
        line.alpha = 22
        val step = (56f * resources.displayMetrics.density).coerceAtLeast(1f)
        var x = -height.toFloat()
        while (x < width * 1.6f) {
            canvas.drawLine(x, 0f, x + height.toFloat(), height.toFloat(), line)
            x += step
        }
    }
}
''')
print('v3.13 dashboard backdrop view written')

# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 25', 'versionCode = 26', 1)
bs = bs.replace('versionName = "3.12.0"', 'versionName = "3.13.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.12.0"', '"appVersion" to "3.13.0"', 1)
cp.write_text(cs)
print('v3.13 version metadata applied')
