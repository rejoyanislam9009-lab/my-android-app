from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# Guide v3.31
# - Bengali-script pronunciation on every existing Saudi Arabic phrase card.
# - Dedicated real-life Conversation center.
# - Dedicated major Saudi regional varieties + A-Z practical roadmap.
# - New activities are isolated from finance/alarm/backup code paths.
sp = Path('app/src/main/java/com/guide/app/SaudiArabicActivity.kt')
s = sp.read_text()

if 'GuideSaudiArabicV331' not in s:
    class_anchor = 'class SaudiArabicActivity : AppCompatActivity() {\n'
    require(s, class_anchor, 'SaudiArabicActivity class')
    s = s.replace(class_anchor, class_anchor + '    // GuideSaudiArabicV331\n', 1)

    search_old = '            val searchMatch = q.isBlank() || listOf(phrase.arabic, phrase.latin, phrase.bangla, phrase.english, phrase.note)\n'
    search_new = '            val searchMatch = q.isBlank() || listOf(phrase.arabic, phrase.latin, SaudiBanglaPronunciation.fromLatin(phrase.latin), phrase.bangla, phrase.english, phrase.note)\n'
    require(s, search_old, 'phrase search fields')
    s = s.replace(search_old, search_new, 1)

    phrase_old = '''        card.addView(text(item.arabic, 27f, "#FFFFFF", true).apply {
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(0, dp(7), 0, 0)
        })
        card.addView(text(item.latin, 12f, "#79D8B7", true).apply {
            gravity = Gravity.END
            setPadding(0, dp(3), 0, dp(9))
        })

        card.addView(labelValue("বাংলা অর্থ", item.bangla, "#F4C57B"))'''
    phrase_new = '''        card.addView(text(item.arabic, 27f, "#FFFFFF", true).apply {
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(0, dp(7), 0, dp(5))
        })
        card.addView(labelValue("বাংলা উচ্চারণ", SaudiBanglaPronunciation.fromLatin(item.latin), "#79D8B7"))
        card.addView(labelValue("English pronunciation", item.latin, "#8FAAF2"))

        card.addView(labelValue("বাংলা অর্থ", item.bangla, "#F4C57B"))'''
    require(s, phrase_old, 'phrase Arabic/Latin block')
    s = s.replace(phrase_old, phrase_new, 1)

    actions_old = '''        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("🎯 Quick Quiz", "#4E447B", 12f) { startQuiz() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(hSpace(8))
        actions.addView(button("★ Favorites", "#765A2D", 12f) {
            selectedCategory = "★ পছন্দ"; renderCategories(); renderPhraseList()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(actions)
        content.addView(space(15))'''
    actions_new = '''        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("🎯 Quick Quiz", "#4E447B", 12f) { startQuiz() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(hSpace(8))
        actions.addView(button("★ Favorites", "#765A2D", 12f) {
            selectedCategory = "★ পছন্দ"; renderCategories(); renderPhraseList()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(actions)
        content.addView(space(8))

        val learningModes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        learningModes.addView(button("💬 কথোপকথন", "#24715E", 11.5f) {
            startActivity(android.content.Intent(this, SaudiConversationActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        learningModes.addView(hSpace(8))
        learningModes.addView(button("🗺 আঞ্চলিক + A–Z", "#6C5527", 11.5f) {
            startActivity(android.content.Intent(this, SaudiDialectActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(learningModes)
        content.addView(space(15))'''
    require(s, actions_old, 'quiz/favorites actions')
    s = s.replace(actions_old, actions_new, 1)

    daily_old = '''            c.addView(text(p.latin, 10.5f, "#72D8B3", true).apply { gravity = Gravity.END })
            c.addView(text("${p.bangla}  •  ${p.english}", 11.5f, "#D3DCEF", true).apply { setPadding(0, dp(5), 0, 0) })'''
    daily_new = '''            c.addView(text(SaudiBanglaPronunciation.fromLatin(p.latin), 11.5f, "#72D8B3", true).apply { gravity = Gravity.END })
            c.addView(text("English pronunciation • ${p.latin}", 9.8f, "#8FAAF2", true).apply { gravity = Gravity.END; setPadding(0, dp(2), 0, 0) })
            c.addView(text("${p.bangla}  •  ${p.english}", 11.5f, "#D3DCEF", true).apply { setPadding(0, dp(5), 0, 0) })'''
    require(s, daily_old, 'daily lesson pronunciation')
    s = s.replace(daily_old, daily_new, 1)

    quiz_old = '            .setTitle("${question.arabic}\\n${question.latin}")\n'
    quiz_new = '            .setTitle("${question.arabic}\\n${SaudiBanglaPronunciation.fromLatin(question.latin)}\\n${question.latin}")\n'
    require(s, quiz_old, 'quiz title')
    s = s.replace(quiz_old, quiz_new, 1)

    hero_old = '        hero.addView(text("Saudi / Gulf কথ্য আরবি • বাংলা + English অর্থ", 14f, "#BCEADA", true).apply { setPadding(0, dp(4), 0, 0) })\n'
    hero_new = '        hero.addView(text("Saudi কথ্য আরবি • বাংলা উচ্চারণ + বাংলা অর্থ + English", 14f, "#BCEADA", true).apply { setPadding(0, dp(4), 0, 0) })\n'
    require(s, hero_old, 'hero subtitle')
    s = s.replace(hero_old, hero_new, 1)

    sp.write_text(s)
    print('v3.31 Saudi Arabic Bengali pronunciation and learning modes applied')
else:
    print('v3.31 Saudi Arabic UI already applied')

# Register the two isolated learning destinations after v3.30 has registered the
# main SaudiArabicActivity.
manifest = Path('app/src/main/AndroidManifest.xml')
xml = manifest.read_text()
if 'android:name=".SaudiConversationActivity"' not in xml:
    anchor = '        <activity android:name=".SaudiArabicActivity" android:exported="false" />\n'
    require(xml, anchor, 'SaudiArabicActivity manifest entry')
    xml = xml.replace(
        anchor,
        anchor + '        <activity android:name=".SaudiConversationActivity" android:exported="false" />\n' +
        '        <activity android:name=".SaudiDialectActivity" android:exported="false" />\n',
        1,
    )
    manifest.write_text(xml)
    print('v3.31 conversation/dialect activities registered')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 44', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.31.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.31.0"', cs, count=1)
    cp.write_text(cs)
print('v3.31 version metadata applied')
