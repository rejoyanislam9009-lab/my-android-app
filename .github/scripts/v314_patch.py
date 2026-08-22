from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# Guide v3.14
# Reference-style animated rainbow gradient border.
# The border thickness is FIXED for the full animation; only shader position
# moves. This removes the v3.13 1dp/2dp pulse that looked like changing px.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideRainbowBorderV314' not in ms:
    start = ms.find('    private fun applyAnimatedCardBorder(')
    end = ms.find('    private fun formBox()', start)
    if start < 0 or end < 0:
        raise SystemExit('v3.13 animated border helper not found')

    helper = r'''    // GuideRainbowBorderV314
    private fun applyAnimatedCardBorder(target: View, fill: String, accent: String, radiusDp: Int = 20) {
        // Keep the physical stroke width constant. Animation changes only the
        // rainbow shader phase, so the border never becomes thicker/thinner.
        val fixedStrokePx = (resources.displayMetrics.density * 1.05f).coerceAtLeast(1f)
        val border = GuideRainbowBorderDrawable(
            fillColor = Color.parseColor(fill),
            accentColor = Color.parseColor(accent),
            cornerRadiusPx = dp(radiusDp).toFloat(),
            strokeWidthPx = fixedStrokePx
        )
        target.background = border
        target.elevation = dp(8).toFloat()

        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5200L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { border.setPhase(it.animatedValue as Float) }
        }
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!animator.isStarted) animator.start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                animator.cancel()
            }
        })
        target.post {
            if (target.isAttachedToWindow && !animator.isStarted) animator.start()
        }
    }

'''
    ms = ms[:start] + helper + ms[end:]
    mp.write_text(ms)
    print('v3.14 MainActivity fixed-width rainbow gradient border applied')
else:
    print('v3.14 MainActivity border patch already applied')

# Custom drawable: a dark premium fill plus a continuously moving rainbow
# sweep around a rounded rectangle. Main stroke and glow are both constant.
border_file = Path('app/src/main/java/com/guide/app/GuideRainbowBorderDrawable.kt')
border_file.write_text(r'''package com.guide.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

class GuideRainbowBorderDrawable(
    private val fillColor: Int,
    private val accentColor: Int,
    private val cornerRadiusPx: Float,
    strokeWidthPx: Float
) : Drawable() {

    private val fixedStrokeWidth = strokeWidthPx.coerceAtLeast(1f)
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private var phase = 0f
    private var sweep: SweepGradient? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = fixedStrokeWidth * 3.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 48
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = fixedStrokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setPhase(value: Float) {
        phase = value - kotlin.math.floor(value)
        updateShaderMatrix()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val half = fixedStrokeWidth / 2f
        rect.set(
            bounds.left + half,
            bounds.top + half,
            bounds.right - half,
            bounds.bottom - half
        )

        fillPaint.shader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            intArrayOf(
                shade(fillColor, 1.16f),
                fillColor,
                shade(fillColor, 0.72f)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )

        val colors = intArrayOf(
            Color.parseColor("#FF304F"),
            Color.parseColor("#FF8A24"),
            Color.parseColor("#FFE13A"),
            Color.parseColor("#45E45A"),
            Color.parseColor("#31E6C7"),
            Color.parseColor("#37B7FF"),
            accentColor,
            Color.parseColor("#6E55FF"),
            Color.parseColor("#E044FF"),
            Color.parseColor("#FF304F")
        )
        val positions = floatArrayOf(
            0f, 0.11f, 0.22f, 0.34f, 0.45f,
            0.56f, 0.66f, 0.77f, 0.89f, 1f
        )
        sweep = SweepGradient(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            colors,
            positions
        ).also {
            strokePaint.shader = it
            glowPaint.shader = it
        }
        updateShaderMatrix()
    }

    private fun updateShaderMatrix() {
        val current = sweep ?: return
        shaderMatrix.reset()
        shaderMatrix.setRotate(
            phase * 360f,
            bounds.exactCenterX(),
            bounds.exactCenterY()
        )
        current.setLocalMatrix(shaderMatrix)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val full = RectF(bounds)
        canvas.drawRoundRect(full, cornerRadiusPx, cornerRadiusPx, fillPaint)

        // Fixed soft neon glow behind a fixed-width crisp rainbow line.
        // Neither width changes during animation.
        val borderRadius = (cornerRadiusPx - fixedStrokeWidth / 2f).coerceAtLeast(0f)
        canvas.drawRoundRect(rect, borderRadius, borderRadius, glowPaint)
        canvas.drawRoundRect(rect, borderRadius, borderRadius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        glowPaint.alpha = (48 * (alpha / 255f)).toInt().coerceIn(0, 48)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun shade(color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }
}
''')
print('v3.14 GuideRainbowBorderDrawable written')

# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 26', 'versionCode = 27', 1)
bs = bs.replace('versionName = "3.13.0"', 'versionName = "3.14.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.13.0"', '"appVersion" to "3.14.0"', 1)
cp.write_text(cs)
print('v3.14 version metadata applied')
