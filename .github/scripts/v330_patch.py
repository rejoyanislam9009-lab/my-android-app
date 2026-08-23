from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# ---------------------------------------------------------------------------
# Guide v3.30
# - New offline Saudi/Gulf conversational Arabic learning center.
# - Arabic + Bengali + English meanings + Latin pronunciation.
# - Search, categories, favorites, learned progress, daily five and quick quiz.
# - Arabic (Saudi) Text-to-Speech when the phone has an ar-SA voice installed.
# - Progress/favorites use guide_ui, so existing Firebase snapshots back them up.
# - Sidebar + Dashboard entry without changing existing accounting/alarm flows.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideSaudiArabicV330' not in ms:
    sidebar_anchor = '        menu.addView(drawerSection("হিসাব ও ট্র্যাকিং"))\n'
    require(ms, sidebar_anchor, 'sidebar tracking section')
    saudi_sidebar = '''        // GuideSaudiArabicV330\n        menu.addView(drawerSection("ভাষা শেখা"))
        menu.addView(drawerItem("ع", "Saudi Arabic • NEW", false) {
            closeDrawer(true)
            startActivity(Intent(this, SaudiArabicActivity::class.java))
        })
        menu.addView(space(10))

'''
    ms = ms.replace(sidebar_anchor, saudi_sidebar + sidebar_anchor, 1)

    # Give the new learning destination a dedicated premium sidebar tone.
    color_anchor = '            label.startsWith("সেটিংস") -> "#3E4B70"\n'
    if color_anchor in ms:
        ms = ms.replace(color_anchor, '            label.startsWith("Saudi Arabic") -> "#246957"\n' + color_anchor, 1)

    # Dashboard shortcut. v3.29 creates the active-functions card immediately
    # before the prayer section; add one self-contained learning card there.
    dash_anchor = '''        root.addView(activeCard)
        root.addView(space(18))

        root.addView(sectionTitle("আজকের নামাজের সময় • NEW"))'''
    require(ms, dash_anchor, 'v3.29 dashboard active card')
    dash_new = '''        root.addView(activeCard)
        root.addView(space(18))

        root.addView(sectionTitle("ভাষা শেখা • NEW"))
        root.addView(rowCard(
            "ع",
            "Saudi Arabic শেখা",
            "Offline • Arabic + বাংলা + English • pronunciation • quiz",
            "#24715E"
        ) {
            startActivity(Intent(this, SaudiArabicActivity::class.java))
        })
        root.addView(space(18))

        root.addView(sectionTitle("আজকের নামাজের সময় • NEW"))'''
    ms = ms.replace(dash_anchor, dash_new, 1)

    mp.write_text(ms)
    print('v3.30 MainActivity Saudi Arabic sidebar/dashboard integration applied')
else:
    print('v3.30 MainActivity integration already applied')


# Manifest: private in-app destination; no new permission is required.
manifest = Path('app/src/main/AndroidManifest.xml')
xml = manifest.read_text()
if 'android:name=".SaudiArabicActivity"' not in xml:
    anchor = '        <activity android:name=".BackupActivity" android:exported="false" />\n'
    require(xml, anchor, 'BackupActivity manifest anchor')
    xml = xml.replace(
        anchor,
        anchor + '        <activity android:name=".SaudiArabicActivity" android:exported="false" />\n',
        1,
    )
    manifest.write_text(xml)
    print('v3.30 SaudiArabicActivity manifest registration applied')
else:
    print('v3.30 manifest already contains SaudiArabicActivity')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 43', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.30.0"', gs, count=1)
gp.write_text(gs)
print('v3.30 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.30.0"', cs, count=1)
    cp.write_text(cs)
    print('v3.30 cloud metadata applied')
