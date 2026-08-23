from pathlib import Path
import re


def function_bounds(text: str, signature: str):
    start = text.find(signature)
    if start < 0:
        return None
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'closing brace not found: {signature}')
    while end < len(text) and text[end] == '\n':
        end += 1
    return start, end


mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideSmartExpenseV338' not in ms:
    bounds = function_bounds(ms, '    private fun moneyPage(): LinearLayout {')
    if not bounds:
        raise SystemExit('moneyPage not found')
    start, end = bounds
    money = ms[start:end]
    return_marker = '        return root\n'
    return_pos = money.rfind(return_marker)
    if return_pos < 0:
        raise SystemExit('moneyPage return root not found')

    smart_block = r'''        // GuideSmartExpenseV338
        root.addView(space(22))
        val smartExpenseCardV338 = card("#17273D", padding = 14)
        smartExpenseCardV338.background = premiumGradientStroke("#17273D", "#536FE1", 1, 20)
        smartExpenseCardV338.addView(text("🧮 Smart হিসাব • Note → Total", 17f, "#FFFFFF", bold = true))
        smartExpenseCardV338.addView(text("ছবির মতো অনেক লাইনের খরচ একসাথে লিখুন। Guide প্রতিটি line-এর amount ধরে Total করবে, কয় ভাগ হবে হিসাব করবে এবং PDF/Share বানাবে।", 11f, "#AEBBDD").apply { setPadding(0, dp(5), 0, 0) })
        smartExpenseCardV338.addView(text("বাংলা/English digit • Decimal • SAR/BDT/USD • Auto calculation", 10.3f, "#78D8B5", bold = true).apply { setPadding(0, dp(7), 0, dp(10)) })
        smartExpenseCardV338.addView(smallAction("✦ নোট লিখে Smart হিসাব করুন", "#405A91") {
            startActivity(android.content.Intent(this, SmartExpenseActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(smartExpenseCardV338)

'''
    money = money[:return_pos] + smart_block + money[return_pos:]
    ms = ms[:start] + money + ms[end:]
    mp.write_text(ms)
    print('v3.38 Smart হিসাব launcher applied')
else:
    print('v3.38 Smart হিসাব launcher already applied')

# Ensure the activity is registered even if an older manifest is restored.
manifest = Path('app/src/main/AndroidManifest.xml')
if manifest.exists():
    text = manifest.read_text()
    if '.SmartExpenseActivity' not in text:
        marker = '        <activity android:name=".PdfReportsActivity" android:exported="false" />\n'
        if marker in text:
            text = text.replace(marker, marker + '        <activity android:name=".SmartExpenseActivity" android:exported="false" />\n', 1)
        else:
            text = text.replace('    </application>', '        <activity android:name=".SmartExpenseActivity" android:exported="false" />\n    </application>', 1)
        manifest.write_text(text)
        print('v3.38 SmartExpenseActivity manifest registration applied')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 51', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.38.0"', gs, count=1)
gp.write_text(gs)
print('v3.38 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = cs.replace('3.37.0', '3.38.0')
    cp.write_text(cs)
    print('v3.38 cloud metadata applied')
