from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# Guide v3.16
# - Premium dashboard-inspired sidebar background.
# - Keeps menu cards readable while adding layered depth, soft glows and
#   geometric linework behind the content.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideSidebarBackdropV316' not in ms:
    old = '''            setPadding(dp(14), dp(18), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#142445"), Color.parseColor("#101B36"), Color.parseColor("#091126"))
            )'''
    new = '''            setPadding(dp(14), dp(18), dp(14), dp(12))
            // GuideSidebarBackdropV316
            background = GuideSidebarBackdropDrawable(this@MainActivity)'''
    ms = req(ms, old, new, 'sidebar premium backdrop')
    mp.write_text(ms)
    print('v3.16 premium sidebar backdrop applied')
else:
    print('v3.16 sidebar patch already applied')

# A custom Drawable scales to every drawer width/height and does not intercept
# scrolling or touch events. The opacity is intentionally low behind menu cards.
dp = Path('app/src/main/java/com/guide/app/GuideSidebarBackdropDrawable.kt')
dp.write_text(r'''package com.guide.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class GuideSidebarBackdropDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density

    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.85f * density
        color = Color.argb(24, 150, 190, 255)
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * density
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()

        base.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.rgb(14, 31, 64),
                Color.rgb(12, 25, 53),
                Color.rgb(7, 16, 36)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, base)
        base.shader = null

        // Soft dashboard-style color fields. They are intentionally translucent
        // so white labels and colorful menu cards retain strong contrast.
        glow.color = Color.argb(38, 70, 103, 255)
        canvas.drawCircle(w * 0.12f, h * 0.18f, w * 0.62f, glow)

        glow.color = Color.argb(28, 126, 70, 255)
        canvas.drawCircle(w * 0.98f, h * 0.34f, w * 0.54f, glow)

        glow.color = Color.argb(24, 20, 210, 198)
        canvas.drawCircle(w * 0.02f, h * 0.72f, w * 0.52f, glow)

        glow.color = Color.argb(22, 255, 92, 170)
        canvas.drawCircle(w * 0.96f, h * 0.90f, w * 0.45f, glow)

        // Fine diagonal texture similar to the dashboard background.
        val step = 54f * density
        var x = -h
        while (x < w + h) {
            canvas.drawLine(x, 0f, x + h, h, lines)
            x += step
        }

        // Two faint glass arcs break up the flat surface without becoming noisy.
        accent.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(
                Color.argb(90, 62, 213, 255),
                Color.argb(75, 133, 92, 255),
                Color.argb(70, 255, 81, 174)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        val arc1 = RectF(-w * 0.45f, h * 0.05f, w * 0.62f, h * 0.27f)
        canvas.drawArc(arc1, 285f, 145f, false, accent)
        val arc2 = RectF(w * 0.48f, h * 0.62f, w * 1.38f, h * 0.86f)
        canvas.drawArc(arc2, 120f, 160f, false, accent)
        accent.shader = null

        // Subtle left-edge highlight creates a premium glass panel separation.
        accent.color = Color.argb(42, 111, 158, 255)
        accent.strokeWidth = 1f * density
        val edge = Path().apply {
            moveTo(0.5f * density, 0f)
            lineTo(0.5f * density, h)
        }
        canvas.drawPath(edge, accent)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
''')

bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 28', 'versionCode = 29', 1)
bs = bs.replace('versionName = "3.15.0"', 'versionName = "3.16.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.15.0"', '"appVersion" to "3.16.0"', 1)
cp.write_text(cs)
print('v3.16 version metadata applied')
