from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# Guide v3.34
# - Adds a separate Islamic Audio Center without changing existing feature flows.
# - Quran: 114 surahs, reciter selection and key-free online streaming.
# - Waz/Nasheed: organized topics + local audio import; no third-party copyrighted
#   recordings are bundled into the APK.
# - Local imported audio stays playable offline using Android persisted document URIs.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideIslamicAudioV334' not in ms:
    # Sidebar: add the audio center as its own premium destination before language learning.
    sidebar_anchor = '        menu.addView(drawerSection("ভাষা শেখা"))\n'
    require(ms, sidebar_anchor, 'language sidebar section')
    sidebar_block = '''        // GuideIslamicAudioV334\n        menu.addView(drawerSection("ইসলামিক অডিও"))
        menu.addView(drawerItem("☪", "কোরআন • ওয়াজ • নাশিদ", false) {
            closeDrawer(true)
            startActivity(Intent(this, IslamicAudioActivity::class.java))
        })
        menu.addView(space(10))

'''
    ms = ms.replace(sidebar_anchor, sidebar_block + sidebar_anchor, 1)

    # Give the destination a dedicated color when the premium drawer helper maps labels.
    color_anchor = '            label.startsWith("Saudi Arabic") -> "#246957"\n'
    if color_anchor in ms:
        ms = ms.replace(color_anchor, '            label.startsWith("কোরআন") -> "#1E6655"\n' + color_anchor, 1)

    # Dashboard shortcut near the language-learning destination.
    dash_anchor = '        root.addView(sectionTitle("ভাষা শেখা • NEW"))\n'
    require(ms, dash_anchor, 'language dashboard section')
    dash_block = '''        root.addView(sectionTitle("ইসলামিক অডিও • NEW"))
        root.addView(rowCard(
            "☪",
            "কোরআন • ওয়াজ • নাশিদ",
            "114 সূরা • Quran streaming • নিজের ওয়াজ/নাশিদ offline audio",
            "#236B59"
        ) {
            startActivity(Intent(this, IslamicAudioActivity::class.java))
        })
        root.addView(space(18))

'''
    ms = ms.replace(dash_anchor, dash_block + dash_anchor, 1)

    mp.write_text(ms)
    print('v3.34 MainActivity Islamic Audio integration applied')
else:
    print('v3.34 MainActivity patch already applied')


# Register the private in-app activity. INTERNET/ACCESS_NETWORK_STATE already exist.
manifest = Path('app/src/main/AndroidManifest.xml')
xml = manifest.read_text()
if 'android:name=".IslamicAudioActivity"' not in xml:
    anchor = '        <activity android:name=".SaudiArabicActivity" android:exported="false" />\n'
    require(xml, anchor, 'SaudiArabicActivity manifest anchor')
    xml = xml.replace(anchor, anchor + '        <activity android:name=".IslamicAudioActivity" android:exported="false" />\n', 1)
    manifest.write_text(xml)
    print('v3.34 IslamicAudioActivity manifest registration applied')
else:
    print('v3.34 manifest already contains IslamicAudioActivity')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 47', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.34.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.34.0"', cs, count=1)
    cp.write_text(cs)
print('v3.34 version metadata applied')
