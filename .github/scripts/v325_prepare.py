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
p.write_text(s)
print('v3.25 prepare: adapted scroll retention to SwipeRefresh shell')
