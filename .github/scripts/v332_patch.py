from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# Guide v3.32
# - Preserve a visible content anchor, not only raw scrollY, when MainActivity
#   rebuilds after Track/Attendance/finance/reminder actions.
# - Add the large offline Saudi vocabulary destination.
# - Keep existing finance/alarm/backup code paths unchanged.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()
if 'GuideScrollAnchorV332' not in ms:
    capture_old = '''        val previousScrollY = guideContentScroll?.scrollY ?: 0

        handler.removeCallbacksAndMessages(null)'''
    capture_new = '''        val previousScrollY = guideContentScroll?.scrollY ?: 0
        val previousBody = guideContentScroll?.getChildAt(0) as? ViewGroup
        var previousAnchorIndex = -1
        var previousAnchorOffset = 0
        if (previousBody != null && previousScrollY > 0) {
            for (i in 0 until previousBody.childCount) {
                val child = previousBody.getChildAt(i)
                if (child.bottom > previousScrollY) {
                    previousAnchorIndex = i
                    previousAnchorOffset = previousScrollY - child.top
                    break
                }
            }
        }

        handler.removeCallbacksAndMessages(null)'''
    require(ms, capture_old, 'render scroll capture')
    ms = ms.replace(capture_old, capture_new, 1)

    restore_old = '''        if (previousPageKey == pageKey && previousScrollY > 0) {
            val restoreY = previousScrollY
            guideContentScroll?.post {
                guideContentScroll?.scrollTo(0, restoreY)
                guideContentScroll?.postDelayed({ guideContentScroll?.scrollTo(0, restoreY) }, 90L)
                guideContentScroll?.postDelayed({ guideContentScroll?.scrollTo(0, restoreY) }, 240L)
            }
        }'''
    restore_new = '''        if (previousPageKey == pageKey && previousScrollY > 0) {
            // GuideScrollAnchorV332: raw scrollY can visibly jump when a card
            // above the viewport changes height. Restore the same direct body
            // child + offset instead, then repeat after late layout passes.
            fun restoreGuideAnchorV332() {
                val newScroll = guideContentScroll ?: return
                val newBody = newScroll.getChildAt(0) as? ViewGroup
                val target = if (
                    newBody != null &&
                    previousAnchorIndex >= 0 &&
                    previousAnchorIndex < newBody.childCount
                ) {
                    newBody.getChildAt(previousAnchorIndex).top + previousAnchorOffset
                } else {
                    previousScrollY
                }
                newScroll.scrollTo(0, target.coerceAtLeast(0))
            }
            guideContentScroll?.post {
                restoreGuideAnchorV332()
                guideContentScroll?.postDelayed({ restoreGuideAnchorV332() }, 40L)
                guideContentScroll?.postDelayed({ restoreGuideAnchorV332() }, 140L)
                guideContentScroll?.postDelayed({ restoreGuideAnchorV332() }, 320L)
            }
        }
        // GuideScrollAnchorV332'''
    require(ms, restore_old, 'v3.29 scroll restore block')
    ms = ms.replace(restore_old, restore_new, 1)
    mp.write_text(ms)
    print('v3.32 MainActivity visual-anchor scroll preservation applied')
else:
    print('v3.32 MainActivity scroll patch already applied')


sp = Path('app/src/main/java/com/guide/app/SaudiArabicActivity.kt')
s = sp.read_text()
if 'GuideSaudiMegaV332' not in s:
    mode_anchor = '''        content.addView(learningModes)
        content.addView(space(15))'''
    mode_new = '''        content.addView(learningModes)
        content.addView(space(8))

        // GuideSaudiMegaV332
        content.addView(button("📚 330+ শব্দ ও Phrase • Mega Library", "#285B78", 11.5f) {
            startActivity(android.content.Intent(this, SaudiMegaVocabularyActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(text("কাজ, টাকা, বাজার, খাবার, গাড়ি, কাগজপত্র, স্বাস্থ্য, মোবাইল, বাসা, worksite ও আরও অনেক বিষয়।", 10.2f, "#7F90B5").apply {
            setPadding(dp(4), dp(5), dp(4), 0)
        })
        content.addView(space(15))'''
    require(s, mode_anchor, 'v3.31 learning mode block')
    s = s.replace(mode_anchor, mode_new, 1)
    sp.write_text(s)
    print('v3.32 Saudi Mega Library entry applied')
else:
    print('v3.32 Saudi Mega entry already applied')

manifest = Path('app/src/main/AndroidManifest.xml')
xml = manifest.read_text()
if 'android:name=".SaudiMegaVocabularyActivity"' not in xml:
    anchor = '        <activity android:name=".SaudiDialectActivity" android:exported="false" />\n'
    require(xml, anchor, 'SaudiDialectActivity manifest entry')
    xml = xml.replace(anchor, anchor + '        <activity android:name=".SaudiMegaVocabularyActivity" android:exported="false" />\n', 1)
    manifest.write_text(xml)
    print('v3.32 Saudi Mega activity registered')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 45', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.32.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.32.0"', cs, count=1)
    cp.write_text(cs)
print('v3.32 version metadata applied')
