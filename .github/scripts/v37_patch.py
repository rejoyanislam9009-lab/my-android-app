from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'private fun showBuiltInSoundPicker' not in ms:
    old = '''    private fun chooseBuiltInAzan() {
        AlertDialog.Builder(this).setTitle("Guide-এর বিল্ট-ইন আজান ও অ্যালার্ট")
            .setItems(bundledAlarmLabels()) { _, which ->
                store.setPrayerAzanUri(bundledAlarmValues()[which])
                ReminderScheduler.scheduleAll(this, store)
                render()
            }.show()
    }

    private fun chooseBuiltInAlarmSound(onSelected: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle("Guide-এর বিল্ট-ইন রিংটোন")
            .setItems(bundledAlarmLabels()) { _, which -> onSelected(bundledAlarmValues()[which]) }
            .show()
    }
'''
    new = '''    private fun chooseBuiltInAzan() {
        showBuiltInSoundPicker("Guide-এর বিল্ট-ইন আজান ও অ্যালার্ট") { uri ->
            store.setPrayerAzanUri(uri)
            ReminderScheduler.scheduleAll(this, store)
            render()
        }
    }

    private fun chooseBuiltInAlarmSound(onSelected: (String) -> Unit) {
        showBuiltInSoundPicker("Guide-এর বিল্ট-ইন রিংটোন", onSelected)
    }

    private fun showBuiltInSoundPicker(title: String, onSelected: (String) -> Unit) {
        AlarmSoundPlayer.stop()
        val labels = bundledAlarmLabels()
        val values = bundledAlarmValues()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }
        val previewButtons = mutableListOf<Pair<Button, String>>()
        var previewing: String? = null
        lateinit var dialog: AlertDialog

        labels.indices.forEach { index ->
            val uri = values[index]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(8), dp(9))
                background = roundedStroke("#252525", "#35FFFFFF", 1, 12)
            }
            row.addView(text(labels[index], 14f, "#FFFFFF", bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val play = pillButton("▶ শুনুন", "#315E55") {
                if (previewing == uri && AlarmSoundPlayer.isActive()) {
                    AlarmSoundPlayer.stop()
                    previewing = null
                } else {
                    AlarmSoundPlayer.stop()
                    previewing = uri
                    AlarmSoundPlayer.start(this, uri, soundEnabled = true, vibrateEnabled = false)
                }
                previewButtons.forEach { (button, value) ->
                    button.text = if (previewing == value && AlarmSoundPlayer.isActive()) "■ বন্ধ" else "▶ শুনুন"
                }
            }
            previewButtons += play to uri
            row.addView(play, LinearLayout.LayoutParams(dp(92), dp(42)))
            row.addView(hSpace(6))
            row.addView(pillButton("✓", "#654BE5") {
                AlarmSoundPlayer.stop()
                previewing = null
                onSelected(uri)
                dialog.dismiss()
            }, LinearLayout.LayoutParams(dp(48), dp(42)))
            root.addView(row)
            if (index < labels.lastIndex) root.addView(space(7))
        }

        val scroll = ScrollView(this).apply { addView(root) }
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("আগে ▶ শুনুন চাপুন, পছন্দ হলে ✓ চাপুন।")
            .setView(scroll)
            .setNegativeButton("বাতিল", null)
            .create()
        dialog.setOnDismissListener { AlarmSoundPlayer.stop() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.78f).toInt())
    }
'''
    ms = req(ms, old, new, 'previewable built-in sound picker')
    mp.write_text(ms)
    print('v3.7 previewable sound picker applied')
else:
    print('v3.7 sound preview patch already applied')

bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 19', 'versionCode = 20', 1)
bs = bs.replace('versionName = "3.6.0"', 'versionName = "3.7.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.6.0"', '"appVersion" to "3.7.0"', 1)
cp.write_text(cs)
print('v3.7 version metadata applied')
