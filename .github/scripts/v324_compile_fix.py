from pathlib import Path

p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()
old = 'confirmDeleteRoomMember(item)'
new = 'deleteRoomMember(item)'
if old in s:
    s = s.replace(old, new)
    p.write_text(s)
    print('v3.24 compile fix: member delete helper reference corrected')
else:
    print('v3.24 compile fix: no replacement needed')
