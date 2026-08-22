from pathlib import Path

p = Path('app/src/main/java/com/guide/app/SaudiMegaVocabularyActivity.kt')
s = p.read_text()
old = '''    private fun lighten(hex:String):String { val c=Color.parseColor(hex); fun f(v:Int)=(v+(255-v)*0.18f).toInt().coerceIn(0,255); return String.format("#%02X%02X%02X",f(Color.red(c)),f(Color.green(c)),f(Color.blue(c))) }'''
new = '''    private fun lighten(hex: String): String {
        val c = Color.parseColor(hex)
        fun channel(value: Int): Int {
            return (value + (255 - value) * 0.18f).toInt().coerceIn(0, 255)
        }
        return String.format(
            "#%02X%02X%02X",
            channel(Color.red(c)),
            channel(Color.green(c)),
            channel(Color.blue(c))
        )
    }'''
if old not in s:
    raise SystemExit('v3.32 mega lighten helper pattern not found')
s = s.replace(old, new, 1)
p.write_text(s)
print('v3.32 Mega Vocabulary helper compile fix applied')
