from pathlib import Path


def fix(path: str) -> None:
    p = Path(path)
    s = p.read_text()
    old_compact = '''    private fun lighten(hex:String):String { val c=Color.parseColor(hex); fun f(v:Int)=(v+(255-v)*0.18f).toInt().coerceIn(0,255); return String.format("#%02X%02X%02X",f(Color.red(c)),f(Color.green(c)),f(Color.blue(c))) }'''
    old_spaced = '''    private fun lighten(hex: String): String {\n        val c = Color.parseColor(hex); fun f(v: Int) = (v + (255 - v) * 0.18f).toInt().coerceIn(0,255)\n        return String.format("#%02X%02X%02X", f(Color.red(c)), f(Color.green(c)), f(Color.blue(c)))\n    }'''
    new = '''    private fun lighten(hex: String): String {\n        val c = Color.parseColor(hex)\n        fun lift(value: Int): Int {\n            return (value + (255 - value) * 0.18f).toInt().coerceIn(0, 255)\n        }\n        return String.format(\n            "#%02X%02X%02X",\n            lift(Color.red(c)),\n            lift(Color.green(c)),\n            lift(Color.blue(c))\n        )\n    }'''
    if old_compact in s:
        s = s.replace(old_compact, new, 1)
    elif old_spaced in s:
        s = s.replace(old_spaced, new, 1)
    elif 'private fun lighten' in s and 'fun lift(value: Int)' in s:
        print(f'{path}: already fixed')
        return
    else:
        raise SystemExit(f'lighten helper pattern not found: {path}')
    p.write_text(s)
    print(f'{path}: v3.31 helper compile fix applied')


fix('app/src/main/java/com/guide/app/SaudiConversationActivity.kt')
fix('app/src/main/java/com/guide/app/SaudiDialectActivity.kt')
