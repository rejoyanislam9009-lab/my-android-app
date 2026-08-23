from pathlib import Path
import re

# Guide v3.40
# Smart Expense parser correctness fix: in notes like `10 tarikh-4 riyal`,
# the hyphen is a separator, not a negative sign. Expense amounts are parsed as
# positive values while date/sequence numbers remain excluded when a separate
# amount exists.
sp = Path('app/src/main/java/com/guide/app/SmartExpenseActivity.kt')
s = sp.read_text()

old = r'''    private fun parseAmount(raw: String): Double? {
        val line = normalizeDigits(raw)
            .replace('٫', '.')
            .replace('٬', ',')
        val number = "(-?\\d+(?:[\\.,]\\d+)?)"
        val currencyRegex = Regex("$number\\s*(?:sar|s\\.?a\\.?r\\.?|riyal|rial|riyals|rials|রিয়াল|রিয়াল|রিয়াল|টাকা|৳|dollar|usd)", RegexOption.IGNORE_CASE)
        val explicit = currencyRegex.findAll(line).lastOrNull()?.groupValues?.getOrNull(1)?.toAmount()
        if (explicit != null) return explicit

        val separators = listOf(" - ", "-", "–", "—", ":", "=")
        val positions = separators.map { line.lastIndexOf(it) }.filter { it >= 0 }
        if (positions.isNotEmpty()) {
            val last = positions.maxOrNull() ?: -1
            val tail = line.substring((last + 1).coerceAtMost(line.length))
            val tailAmount = Regex(number).findAll(tail).lastOrNull()?.value?.toAmount()
            if (tailAmount != null) return tailAmount
        }

        val containsDateWord = Regex("(?:তারিখ|তারিক|tarikh|date)", RegexOption.IGNORE_CASE).containsMatchIn(line)
        val allNumbers = Regex(number).findAll(line).mapNotNull { it.value.toAmount() }.toList()
        if (containsDateWord && allNumbers.size <= 1) return null
        return allNumbers.lastOrNull()
    }
'''

new = r'''    private fun parseAmount(raw: String): Double? {
        // GuideSmartExpenseParserV340
        // A dash in an expense note is a field separator, not a minus sign.
        // Examples: `10 tarikh-4 riyal` => 4.00, `পানি-7` => 7.00.
        val line = normalizeDigits(raw)
            .replace('٫', '.')
            .replace('٬', ',')
            .trim()

        // Deliberately unsigned: Smart হিসাব is an expense-entry calculator.
        // This prevents `tarikh-14` from becoming -14.
        val number = "(\\d+(?:[\\.,]\\d+)?)"
        val currency = "(?:sar|s\\.?a\\.?r\\.?|riyal|rial|riyals|rials|রিয়াল|রিয়াল|টাকা|৳|dollar|usd|ريال|﷼)"

        // Prefer an amount explicitly attached to a currency label.
        val suffixCurrency = Regex("$number\\s*$currency", RegexOption.IGNORE_CASE)
        val suffixAmount = suffixCurrency.findAll(line).lastOrNull()?.groupValues?.getOrNull(1)?.toAmount()
        if (suffixAmount != null) return suffixAmount

        // Also accept formats such as `SAR 12.50` / `ريال 12.50`.
        val prefixCurrency = Regex("$currency\\s*$number", RegexOption.IGNORE_CASE)
        val prefixAmount = prefixCurrency.findAll(line).lastOrNull()?.groupValues?.getOrNull(1)?.toAmount()
        if (prefixAmount != null) return prefixAmount

        // For free-form lines, read the last numeric token after the last
        // separator. The separator itself is never included in the number.
        val separators = listOf(" - ", "-", "–", "—", ":", "=")
        val matches = separators.mapNotNull { separator ->
            val pos = line.lastIndexOf(separator)
            if (pos >= 0) pos to separator.length else null
        }
        if (matches.isNotEmpty()) {
            val (position, separatorLength) = matches.maxByOrNull { it.first } ?: (-1 to 0)
            if (position >= 0) {
                val tail = line.substring((position + separatorLength).coerceAtMost(line.length))
                val tailAmount = Regex(number).findAll(tail).lastOrNull()?.groupValues?.getOrNull(1)?.toAmount()
                if (tailAmount != null) return tailAmount
            }
        }

        val containsDateWord = Regex("(?:তারিখ|তারিক|tarikh|date)", RegexOption.IGNORE_CASE).containsMatchIn(line)
        val allNumbers = Regex(number).findAll(line)
            .mapNotNull { it.groupValues.getOrNull(1)?.toAmount() }
            .toList()
        if (containsDateWord && allNumbers.size <= 1) return null
        return allNumbers.lastOrNull()
    }
'''

if 'GuideSmartExpenseParserV340' not in s:
    if old not in s:
        raise SystemExit('v3.40 parseAmount anchor not found')
    s = s.replace(old, new, 1)
    sp.write_text(s)
    print('v3.40 Smart Expense parser correctness fix applied')
else:
    print('v3.40 Smart Expense parser already applied')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \\d+', 'versionCode = 53', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.40.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text().replace('3.39.0', '3.40.0')
    cp.write_text(cs)

print('v3.40 version metadata applied')
