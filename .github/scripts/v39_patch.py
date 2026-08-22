from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# MainActivity: lightweight premium / faux-3D visual system.
# No 3D engine is used; native gradients, elevation and press motion keep it
# fast on budget Android phones while giving the UI real visual depth.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'private fun premiumGradient(hex: String, radiusDp: Int)' not in ms:
    old_helpers = '''    private fun sectionTitle(value: String) = text(value, 13f, "#A8B4D7", bold = true).apply { setPadding(dp(2), 0, 0, dp(9)); letterSpacing = 0.04f }
    private fun statusPill(value: String, bg: String, fg: String) = text(value, 12f, fg, bold = true).apply { gravity = Gravity.CENTER; background = rounded(bg, 14); setPadding(dp(11), dp(7), dp(11), dp(7)) }
    private fun text(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply { this.text = value; textSize = size; setTextColor(Color.parseColor(color)); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun pillButton(label: String, bg: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(bg, 13); setOnClickListener { action() } }
    private fun smallAction(label: String, bg: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(bg, 12); setOnClickListener { action() } }
    private fun card(hex: String, padding: Int = 17) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); background = rounded(hex, 20); elevation = dp(2).toFloat() }
    private fun formBox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(4)) }
    private fun input(hintText: String) = EditText(this).apply { hint = hintText; setSingleLine(true); setPadding(dp(14), dp(10), dp(14), dp(10)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = dp(radiusDp).toFloat() }
    private fun roundedStroke(fill: String, stroke: String, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(fill)); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(strokeDp), Color.parseColor(stroke)) }
    private fun gradient(top: String, bottom: String) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor(top), Color.parseColor(bottom)))
'''
    new_helpers = '''    private fun sectionTitle(value: String) = text(value, 13f, "#B9C5EA", bold = true).apply { setPadding(dp(2), 0, 0, dp(9)); letterSpacing = 0.045f }
    private fun statusPill(value: String, bg: String, fg: String) = text(value, 12f, fg, bold = true).apply {
        gravity = Gravity.CENTER
        background = premiumGradientStroke(bg, "#36FFFFFF", 1, 14)
        setPadding(dp(11), dp(7), dp(11), dp(7))
        elevation = dp(2).toFloat()
    }
    private fun text(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply { this.text = value; textSize = size; setTextColor(Color.parseColor(color)); if (bold) setTypeface(typeface, Typeface.BOLD) }

    private fun pillButton(label: String, bg: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = premiumGradient(bg, 13)
        elevation = dp(7).toFloat()
        applyDepthPress(7)
        setOnClickListener { action() }
    }

    private fun smallAction(label: String, bg: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = premiumGradient(bg, 12)
        elevation = dp(5).toFloat()
        applyDepthPress(5)
        setOnClickListener { action() }
    }

    private fun card(hex: String, padding: Int = 17) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        background = premiumGradientStroke(hex, "#2EFFFFFF", 1, 20)
        elevation = dp(7).toFloat()
        translationZ = dp(1).toFloat()
        applyDepthPress(7)
    }

    private fun formBox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(4)) }
    private fun input(hintText: String) = EditText(this).apply { hint = hintText; setSingleLine(true); setPadding(dp(14), dp(10), dp(14), dp(10)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }

    private fun shadeColor(hex: String, factor: Float): Int {
        val c = Color.parseColor(hex)
        val a = Color.alpha(c)
        val r = (Color.red(c) * factor).roundToInt().coerceIn(0, 255)
        val g = (Color.green(c) * factor).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(c) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun premiumGradient(hex: String, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(shadeColor(hex, 1.18f), Color.parseColor(hex), shadeColor(hex, 0.72f))
    ).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun premiumGradientStroke(fill: String, stroke: String, strokeDp: Int, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(shadeColor(fill, 1.16f), Color.parseColor(fill), shadeColor(fill, 0.70f))
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(strokeDp), Color.parseColor(stroke))
    }

    private fun View.applyDepthPress(depthDp: Int = 6) {
        elevation = dp(depthDp).toFloat()
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.982f).scaleY(0.982f).translationZ(0f).setDuration(75).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).translationZ(dp(1).toFloat()).setDuration(120).start()
            }
            false
        }
    }

    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = dp(radiusDp).toFloat() }
    private fun roundedStroke(fill: String, stroke: String, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply { setColor(Color.parseColor(fill)); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(strokeDp), Color.parseColor(stroke)) }
    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(top), shadeColor(top, 0.86f), Color.parseColor(bottom))
    )
'''
    ms = req(ms, old_helpers, new_helpers, 'premium helper system')

    ms = req(
        ms,
        '            background = rounded("#0D1630", 0)\n            elevation = dp(8).toFloat()',
        '            background = premiumGradient("#0D1630", 0)\n            elevation = dp(12).toFloat()\n            translationZ = dp(2).toFloat()',
        'premium top bar'
    )
    ms = req(
        ms,
        '            background = rounded("#6A56F4", 15)\n            setOnClickListener { openDrawer() }',
        '            background = premiumGradientStroke("#6A56F4", "#66FFFFFF", 1, 15)\n            elevation = dp(8).toFloat()\n            translationZ = dp(2).toFloat()\n            applyDepthPress(8)\n            setOnClickListener { openDrawer() }',
        'premium avatar'
    )
    ms = req(
        ms,
        '            background = roundedStroke(fill, if (active) "#D4CCFF" else "#66FFFFFF", if (active) 2 else 1, 16)\n            elevation = dp(if (active) 4 else 2).toFloat()',
        '            background = premiumGradientStroke(fill, if (active) "#E7E2FF" else "#54FFFFFF", if (active) 2 else 1, 16)\n            elevation = dp(if (active) 9 else 5).toFloat()\n            translationZ = dp(if (active) 2 else 1).toFloat()\n            applyDepthPress(if (active) 9 else 5)',
        '3d drawer cards'
    )
    ms = req(
        ms,
        '                background = rounded(if (active) "#6553D9" else "#243254", 12)',
        '                background = premiumGradient(if (active) "#735CFF" else "#2E416C", 12)\n                elevation = dp(4).toFloat()',
        '3d drawer icon tiles'
    )

    # Hero/banner depth from v2.8.
    hero_old = '''            ).apply { cornerRadius = dp(24).toFloat() }
            elevation = dp(8).toFloat()
        }
        val bannerTop = LinearLayout(this).apply {'''
    hero_new = '''            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
            elevation = dp(16).toFloat()
            translationZ = dp(3).toFloat()
        }
        val bannerTop = LinearLayout(this).apply {'''
    ms = req(ms, hero_old, hero_new, 'dashboard hero depth')
    ms = req(
        ms,
        '            background = rounded("#32FFFFFF", 18)\n        }, LinearLayout.LayoutParams(dp(54), dp(54)))',
        '            background = premiumGradientStroke("#3558A8FF", "#72FFFFFF", 1, 18)\n            elevation = dp(9).toFloat()\n            translationZ = dp(2).toFloat()\n        }, LinearLayout.LayoutParams(dp(54), dp(54)))',
        'dashboard 3d logo badge'
    )

    # Quick cards get a more dimensional accent tile.
    ms = req(
        ms,
        '            background = rounded(accent, 14)\n        }, LinearLayout.LayoutParams(dp(48), dp(48)))',
        '            background = premiumGradientStroke(accent, "#55FFFFFF", 1, 14)\n            elevation = dp(7).toFloat()\n            translationZ = dp(2).toFloat()\n        }, LinearLayout.LayoutParams(dp(48), dp(48)))',
        'quick action 3d accent tiles'
    )

    mp.write_text(ms)
    print('v3.9 MainActivity premium 3D UI applied')
else:
    print('v3.9 MainActivity premium UI already applied')

# ---------------------------------------------------------------------------
# LoginActivity: match the same premium design language.
# ---------------------------------------------------------------------------
lp = Path('app/src/main/java/com/guide/app/LoginActivity.kt')
ls = lp.read_text()

if 'private fun premiumPanelGradient' not in ls:
    ls = req(
        ls,
        '''    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = rounded("#17203D", 24)
        elevation = dp(5).toFloat()
    }
''',
        '''    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = premiumPanelGradient("#17203D", 24)
        elevation = dp(14).toFloat()
        translationZ = dp(2).toFloat()
    }
''',
        'premium auth panel'
    )
    ls = req(
        ls,
        '        background = rounded("#0F1730", 15)\n    }',
        '        background = premiumPanelGradient("#0F1730", 15)\n        elevation = dp(2).toFloat()\n    }',
        'premium auth fields'
    )
    ls = req(
        ls,
        '''        background = gradientCard("#7457FF", "#5F7CFF", 15)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
    }
''',
        '''        background = gradientCard("#8063FF", "#526FFF", 15)
        elevation = dp(10).toFloat()
        translationZ = dp(2).toFloat()
        applyPressDepth(10)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
    }
''',
        'premium primary auth button'
    )
    ls = req(
        ls,
        '''        background = rounded("#202B4D", 14)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
    }
''',
        '''        background = premiumPanelGradient("#202B4D", 14)
        elevation = dp(5).toFloat()
        applyPressDepth(5)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
    }
''',
        'premium secondary auth button'
    )

    marker = '''    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply {
'''
    helpers = '''    private fun shadeColor(hex: String, factor: Float): Int {
        val c = Color.parseColor(hex)
        val a = Color.alpha(c)
        val r = (Color.red(c) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun premiumPanelGradient(hex: String, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(shadeColor(hex, 1.18f), Color.parseColor(hex), shadeColor(hex, 0.72f))
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), Color.parseColor("#30FFFFFF"))
    }

    private fun android.view.View.applyPressDepth(depthDp: Int) {
        elevation = dp(depthDp).toFloat()
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.982f).scaleY(0.982f).translationZ(0f).setDuration(75).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).translationZ(dp(1).toFloat()).setDuration(120).start()
            }
            false
        }
    }

'''
    ls = req(ls, marker, helpers + marker, 'login premium helper insertion')
    ls = req(
        ls,
        '''            background = gradientCard("#7457FF", "#5B8CFF", 26)
            elevation = dp(10).toFloat()
''',
        '''            background = gradientCard("#8A68FF", "#4F78FF", 26)
            elevation = dp(18).toFloat()
            translationZ = dp(4).toFloat()
''',
        '3d login logo'
    )
    ls = req(
        ls,
        '        GradientDrawable.Orientation.TOP_BOTTOM,\n        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))',
        '        GradientDrawable.Orientation.TL_BR,\n        intArrayOf(Color.parseColor(top), shadeColor(top, 0.82f), Color.parseColor(bottom))',
        'diagonal premium login background'
    )
    lp.write_text(ls)
    print('v3.9 LoginActivity premium 3D UI applied')
else:
    print('v3.9 LoginActivity premium UI already applied')

# ---------------------------------------------------------------------------
# Version bump.
# ---------------------------------------------------------------------------
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 21', 'versionCode = 22', 1)
bs = bs.replace('versionName = "3.8.0"', 'versionName = "3.9.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.8.0"', '"appVersion" to "3.9.0"', 1)
cp.write_text(cs)
print('v3.9 version metadata applied')
