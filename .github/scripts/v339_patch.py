from pathlib import Path
import re

# Guide v3.39
# Fix nested scrolling inside Smart Expense note editor. The EditText consumes
# vertical swipes while it can scroll, then hands the gesture back to the outer
# page at its top/bottom edge. Long notes are no longer limited to 16 lines.
sp = Path('app/src/main/java/com/guide/app/SmartExpenseActivity.kt')
s = sp.read_text()

if 'GuideSmartExpenseNestedScrollV339' not in s:
    if 'import android.view.MotionEvent\n' not in s:
        s = s.replace('import android.view.Gravity\n', 'import android.view.Gravity\nimport android.view.MotionEvent\n', 1)
    if 'import android.text.method.ScrollingMovementMethod\n' not in s:
        s = s.replace('import android.text.TextWatcher\n', 'import android.text.TextWatcher\nimport android.text.method.ScrollingMovementMethod\n', 1)

    old = '''        ).apply {\n            gravity = Gravity.TOP or Gravity.START\n            minLines = 7\n            maxLines = 16\n            setPadding(dp(14), dp(13), dp(14), dp(13))\n        }'''
    new = '''        ).apply {\n            // GuideSmartExpenseNestedScrollV339\n            gravity = Gravity.TOP or Gravity.START\n            minLines = 7\n            maxLines = Int.MAX_VALUE\n            setPadding(dp(14), dp(13), dp(14), dp(13))\n            isVerticalScrollBarEnabled = true\n            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET\n            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS\n            setHorizontallyScrolling(false)\n            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES\n            movementMethod = ScrollingMovementMethod.getInstance()\n\n            var lastTouchYV339 = 0f\n            setOnTouchListener { view, event ->\n                when (event.actionMasked) {\n                    MotionEvent.ACTION_DOWN -> {\n                        lastTouchYV339 = event.y\n                        view.parent?.requestDisallowInterceptTouchEvent(true)\n                    }\n                    MotionEvent.ACTION_MOVE -> {\n                        val deltaY = event.y - lastTouchYV339\n                        if (kotlin.math.abs(deltaY) > 1f) {\n                            val direction = if (deltaY > 0f) -1 else 1\n                            val innerCanScroll = canScrollVertically(direction)\n                            view.parent?.requestDisallowInterceptTouchEvent(innerCanScroll)\n                            lastTouchYV339 = event.y\n                        }\n                    }\n                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {\n                        view.parent?.requestDisallowInterceptTouchEvent(false)\n                    }\n                }\n                false\n            }\n        }'''
    if old not in s:
        raise SystemExit('v3.39 note editor anchor not found')
    s = s.replace(old, new, 1)
    sp.write_text(s)
    print('v3.39 Smart Expense nested note scrolling applied')
else:
    print('v3.39 Smart Expense nested note scrolling already applied')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \\d+', 'versionCode = 52', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.39.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text().replace('3.38.0', '3.39.0')
    cp.write_text(cs)

print('v3.39 version metadata applied')
