from pathlib import Path

p = Path('app/src/main/java/com/guide/app/PdfReportsActivity.kt')
s = p.read_text()
marker = '// GuideFinanceHistoryV326CompileFix'
if marker not in s:
    s = s.replace('LocalDate.parse(completedDate)', 'java.time.LocalDate.parse(completedDate)')
    s = s.replace('LocalDate.parse(payment.date)', 'java.time.LocalDate.parse(payment.date)')
    s += '\n' + marker + '\n'
    p.write_text(s)
    print('v3.26 compile fix: qualified LocalDate in history PDF')
else:
    print('v3.26 compile fix already applied')
