from pathlib import Path

p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()
old = 'ensureAlarmSystemReady(enabledCheck.isChecked)'
if old in s:
    s = s.replace(old, 'ensureAlarmSystemReady(items.any { it.enabled })')
    p.write_text(s)
    print('v3.18 compile fix: alarm readiness uses saved alarm list scope')
else:
    print('v3.18 compile fix: no out-of-scope enabledCheck reference found')
