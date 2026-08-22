from pathlib import Path

p = Path('app/src/main/java/com/guide/app/MainActivity.kt')
s = p.read_text()

needle = 'applyAnimatedCardBorder(this, "#17213E", accent)'
row_start = s.find('    private fun rowCard(')
row_end = s.find('    private fun itemCard(', row_start)
compact_start = s.find('    private fun compactAction(')

# v3.13 originally replaced the first generic card pattern in the file.
# If that occurrence is outside rowCard/compactAction it has no `accent`
# parameter and Kotlin correctly rejects it. Give that accidental occurrence
# a safe fixed accent, then scope the intended animation to rowCard itself.
first = s.find(needle)
if first >= 0 and not (row_start <= first < row_end) and not (compact_start <= first):
    s = s[:first] + s[first:].replace(needle, 'applyAnimatedCardBorder(this, "#17213E", "#6F7CFF")', 1)
    print('v3.13 compile fix: removed out-of-scope accent reference')

# Ensure rowCard gets the requested animated accent in its own function scope.
row_start = s.find('    private fun rowCard(')
row_end = s.find('    private fun itemCard(', row_start)
if row_start < 0 or row_end < 0:
    raise SystemExit('rowCard block not found')
row = s[row_start:row_end]
if 'applyAnimatedCardBorder' not in row:
    old = 'val c = card("#17213E")'
    if old not in row:
        raise SystemExit('rowCard card creation not found')
    row = row.replace(old, 'val c = card("#17213E").apply { applyAnimatedCardBorder(this, "#17213E", accent) }', 1)
    s = s[:row_start] + row + s[row_end:]
    print('v3.13 compile fix: scoped animated border to rowCard')

p.write_text(s)
