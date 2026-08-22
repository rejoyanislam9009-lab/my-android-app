from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# Guide v3.27
# - Restore always-visible Room/Mess member cards and controls.
# - Keep completed-expense auto-archive from v3.26.
# - Remove History controls/cards from the live finance page.
# - Expose completed Room/Mess History only from the premium sidebar.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideFinanceSidebarHistoryV327' not in ms:
    # 1) Sidebar: add a dedicated History destination beside tracking summary.
    old_track = '''        menu.addView(drawerPair(
            drawerItem("◎", "হাজিরা", detailPage == "attendance") { navigate("track", "attendance") },
            drawerItem("▣", "হিসাব", detailPage == "money") { navigate("track", "money") }
        ))
        menu.addView(drawerItem("◉", "ট্র্যাকিং সারাংশ", currentTab == "track" && detailPage == null) { navigate("track") })'''
    new_track = '''        menu.addView(drawerPair(
            drawerItem("◎", "হাজিরা", detailPage == "attendance") { navigate("track", "attendance") },
            drawerItem("▣", "হিসাব", detailPage == "money") { navigate("track", "money") }
        ))
        menu.addView(drawerPair(
            drawerItem("🗂", "হিসাব History", false) { closeDrawer(true); showRoomMessHistory() },
            drawerItem("◉", "ট্র্যাকিং সারাংশ", currentTab == "track" && detailPage == null) { navigate("track") }
        ))'''
    require(ms, old_track, 'tracking sidebar block')
    ms = ms.replace(old_track, new_track, 1)

    # Give the new sidebar History item its own premium color.
    color_anchor = '            label.startsWith("হিসাব") -> "#246377"\n'
    require(ms, color_anchor, 'drawer হিসাব color')
    ms = ms.replace(
        color_anchor,
        '            label.startsWith("হিসাব History") -> "#554583"\n' + color_anchor,
        1,
    )

    # 2) Live Finance page: remove History button; keep Other Bill full-width.
    old_actions = '''        val actionRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow2.addView(smallAction("+ অন্যান্য বিল", "#5B557E") { addRoomExpense(defaultKind = FinanceStore.KIND_ELECTRICITY) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionRow2.addView(hSpace(7))
        actionRow2.addView(smallAction("🗂 History", "#385B86") { showRoomMessHistory() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actionRow2)'''
    new_actions = '''        root.addView(smallAction("+ অন্যান্য বিল", "#5B557E") { addRoomExpense(defaultKind = FinanceStore.KIND_ELECTRICITY) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))'''
    require(ms, old_actions, 'live history action row')
    ms = ms.replace(old_actions, new_actions, 1)

    # 3) Restore the member list as an always-visible control area.
    member_start_marker = '        // Member list is intentionally collapsed by default so the finance page\n'
    member_end_marker = '        fun addActiveExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>) {'
    start = ms.find(member_start_marker)
    end = ms.find(member_end_marker, start)
    if start < 0 or end < 0:
        raise SystemExit('v3.26 member dropdown block not found')

    visible_members = r'''        // GuideFinanceSidebarHistoryV327
        // Member controls are always visible again, matching the simpler v3.25
        // workflow. Completed expenses still disappear from active totals/history.
        root.addView(space(20))
        root.addView(sectionTitle("সদস্য ও Payment"))
        root.addView(text("Member card ট্যাপ করুন → Payment amount, Full Complete, Edit, Active/Inactive এবং Delete সব এখান থেকেই করা যাবে।", 11f, "#8796B9").apply { setPadding(0, dp(3), 0, dp(9)) })

        if (members.isEmpty()) {
            root.addView(emptyCard("কোনো Room/Mess member নেই", "নাম দিয়ে সদস্য যোগ করুন।"))
        } else {
            val activeExpenseIds = activeExpenses.map { it.id }.toSet()
            members.forEachIndexed { index, member ->
                val memberActiveExpenses = activeExpenses.filter { member.id in it.participantIds }
                val ownShare = memberActiveExpenses.sumOf { it.shareAmount() }
                val ownDue = memberActiveExpenses.sumOf { financeRoom.remainingForExpense(it, member.id) }
                val paidNow = allPayments.filter { it.memberId == member.id && it.expenseId in activeExpenseIds }.sumOf { it.amount }
                val status = when {
                    ownShare < 0.005 -> "বর্তমান বাকি নেই"
                    ownDue < 0.005 -> "✓ CURRENT COMPLETE"
                    else -> "⏳ বাকি ${moneyText(ownDue)}"
                }
                val c = card(if (ownShare > 0.005 && ownDue < 0.005) "#16302B" else "#151F37", padding = 13)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(member.name, 16f, if (member.active) "#FFFFFF" else "#8995AF", bold = true))
                labels.addView(text(if (member.active) "● Active member" else "○ Inactive member", 10.7f, if (member.active) "#69CBAA" else "#7381A2"))
                labels.addView(text("চলমান share ${moneyText(ownShare)} • দিয়েছেন ${moneyText(paidNow)}", 11f, "#99A6C5").apply { setPadding(0, dp(4), 0, 0) })
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text(status, 12.0f, if (ownDue > 0.005) "#FF9FA4" else "#70D7B0", bold = true))
                c.addView(row)
                c.setOnClickListener { roomMemberActions(member) }
                root.addView(c)
                if (index < members.lastIndex) root.addView(space(7))
            }
        }

'''
    ms = ms[:start] + visible_members + ms[end:]

    # 4) Remove the completed-history card from the bottom of the live page.
    history_start_marker = '        root.addView(space(22))\n        val historyHero = card("#172641", padding = 14)\n'
    history_end_marker = '        root.addView(historyHero)\n'
    hstart = ms.find(history_start_marker)
    hend = ms.find(history_end_marker, hstart)
    if hstart < 0 or hend < 0:
        raise SystemExit('v3.26 live history hero block not found')
    hend += len(history_end_marker)
    ms = ms[:hstart] + ms[hend:]

    mp.write_text(ms)
    print('v3.27 visible member controls + sidebar-only history applied')
else:
    print('v3.27 MainActivity patch already applied')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 40', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.27.0"', gs, count=1)
gp.write_text(gs)
print('v3.27 version metadata applied')

# Cloud metadata where the version text exists.
cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = cs.replace('3.26.0', '3.27.0')
    cp.write_text(cs)
    print('v3.27 cloud metadata applied')
