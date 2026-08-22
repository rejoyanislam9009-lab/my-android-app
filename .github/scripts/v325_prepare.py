from pathlib import Path

p = Path('.github/scripts/v325_patch.py')
s = p.read_text()
start_marker = "    old_scroll = '''"
end_marker = "    # Premium member action dialog"
start = s.find(start_marker)
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('v325 scroll patch block not found')
replacement = '''    page_scroll_anchor = '        val refresh = SwipeRefreshLayout(this).apply {'
    require(ms, page_scroll_anchor, 'SwipeRefresh main content anchor')
    ms = ms.replace(
        page_scroll_anchor,
        '        guideContentScroll = pageScroll\\n        pageScroll.isVerticalScrollBarEnabled = false\\n' + page_scroll_anchor,
        1,
    )

'''
s = s[:start] + replacement + s[end:]
# PdfWriter is a nested class. Format its local numeric values directly instead
# of relying on the enclosing GuidePdfReport.money helper.
s = s.replace('money(totalValue)', 'String.format(Locale.US, "%.2f", totalValue)')
s = s.replace('money(paidValue)', 'String.format(Locale.US, "%.2f", paidValue)')
s = s.replace('money(dueValue)', 'String.format(Locale.US, "%.2f", dueValue)')
p.write_text(s)
print('v3.25 prepare: adapted scroll shell and PDF number formatting')
