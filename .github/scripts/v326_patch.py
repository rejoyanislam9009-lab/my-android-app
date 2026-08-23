from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


def function_bounds(text: str, signature: str):
    start = text.find(signature)
    if start < 0:
        return None
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'closing brace not found: {signature}')
    while end < len(text) and text[end] == '\n':
        end += 1
    return start, end


def replace_function(text: str, signature: str, replacement: str) -> str:
    bounds = function_bounds(text, signature)
    if not bounds:
        raise SystemExit(f'function not found: {signature}')
    start, end = bounds
    return text[:start] + replacement + text[end:]


# ---------------------------------------------------------------------------
# Guide v3.26
# - Active-cycle Room/Mess accounting: completed items leave the live totals.
# - Completed items automatically appear in a detailed month history.
# - Collapsible member list.
# - Per-month history PDF / WhatsApp export.
# - Full month/date labels and full payment-event audit trail.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideFinanceHistoryV326' not in ms:
    bounds = function_bounds(ms, '    private fun moneyPage(): LinearLayout {')
    if not bounds:
        raise SystemExit('moneyPage not found')
    start, end = bounds
    money = ms[start:end]
    marker = '        // GuideRoomMessSimpleV324\n'
    require(money, marker, 'v3.24 Room/Mess marker')
    room_start = money.index(marker)
    return_marker = '        return root\n'
    return_pos = money.rfind(return_marker)
    if return_pos < room_start:
        raise SystemExit('moneyPage return marker not found')

    room_tail = r'''        // GuideFinanceHistoryV326
        val financeRoom = finance
        val allRoomMembers = financeRoom.roomMembers()
        val members = allRoomMembers.filter { !it.deleted }
        val activeMembers = members.filter { it.active }
        val allExpenses = financeRoom.roomExpenses()
        val allPayments = financeRoom.roomExpensePayments()

        fun roomDue(item: RoomExpense) = financeRoom.expenseCollection(item).third
        fun roomPaid(item: RoomExpense) = financeRoom.expenseCollection(item).second
        fun total(items: List<RoomExpense>) = items.sumOf { it.amount }
        fun paid(items: List<RoomExpense>) = items.sumOf { roomPaid(it) }
        fun due(items: List<RoomExpense>) = items.sumOf { roomDue(it) }

        // ACTIVE is based on remaining money, not the calendar month. As soon as
        // an expense reaches zero due it automatically disappears from the live
        // dashboard and remains permanently available in History.
        val activeExpenses = allExpenses.filter { it.amount > 0.005 && roomDue(it) > 0.005 }
        val completedExpenses = allExpenses.filter { it.amount > 0.005 && roomDue(it) <= 0.005 }
        val activeRent = activeExpenses.filter { it.kind == FinanceStore.KIND_RENT }
        val activeMess = activeExpenses.filter { it.kind == FinanceStore.KIND_MARKET }
        val activeOther = activeExpenses.filter { it.kind != FinanceStore.KIND_RENT && it.kind != FinanceStore.KIND_MARKET }

        root.addView(space(22))
        root.addView(sectionTitle("Room / Mess হিসাব"))
        root.addView(text("চলমান হিসাব আর History আলাদা। কোনো Rent/Mess পুরো Paid হলেই সেটা এখান থেকে সরে History-তে যাবে।", 11.3f, "#8492B6").apply { setPadding(0, dp(3), 0, dp(10)) })

        val rentTotal = total(activeRent)
        val rentPaid = paid(activeRent)
        val rentDue = due(activeRent)
        val rentHero = card(if (activeRent.isEmpty()) "#18243A" else "#1B263B")
        rentHero.addView(text("🏠 চলমান Room Rent", 13f, "#AEBBD5", bold = true))
        rentHero.addView(text("মোট ${moneyText(rentTotal)}", 25f, "#F0C17A", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        rentHero.addView(text("✓ Paid ${moneyText(rentPaid)}", 14f, "#6FD5AE", bold = true).apply { setPadding(0, dp(6), 0, 0) })
        rentHero.addView(text("⏳ Pending ${moneyText(rentDue)}", 14f, if (rentDue < 0.005) "#91A0C1" else "#FF9EA4", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        rentHero.addView(text(if (activeRent.isEmpty()) "✓ কোনো চলমান Room Rent নেই • নতুন Rent যোগ করলে এখান থেকে শুরু হবে" else "${activeRent.size}টি চলমান Rent • Complete হলেই History-তে চলে যাবে", 11.2f, if (activeRent.isEmpty()) "#78CDAF" else "#8F9DBD", bold = activeRent.isEmpty()).apply { setPadding(0, dp(7), 0, 0) })
        root.addView(rentHero)
        root.addView(space(9))

        val messTotal = total(activeMess)
        val messPaid = paid(activeMess)
        val messDue = due(activeMess)
        val messHero = card("#172A37")
        messHero.addView(text("🛒 চলমান Mess / বাজার", 13f, "#AEBBD5", bold = true))
        messHero.addView(text("মোট ${moneyText(messTotal)}  •  Paid ${moneyText(messPaid)}  •  Pending ${moneyText(messDue)}", 14.2f, if (activeMess.isEmpty()) "#91A0C1" else "#73D2B2", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        if (activeMess.isEmpty()) messHero.addView(text("কোনো চলমান Mess/বাজার হিসাব নেই", 10.8f, "#7F8EAE").apply { setPadding(0, dp(4), 0, 0) })
        root.addView(messHero)
        root.addView(space(10))

        val actionRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow1.addView(smallAction("+ সদস্য", "#425B86") { addRoomMember() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionRow1.addView(hSpace(7))
        actionRow1.addView(smallAction("+ রুম ভাড়া", "#8A5C36") { addRoomExpense(defaultKind = FinanceStore.KIND_RENT) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionRow1.addView(hSpace(7))
        actionRow1.addView(smallAction("+ Mess খরচ", "#2E7C70") { addRoomExpense(defaultKind = FinanceStore.KIND_MARKET) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actionRow1)
        root.addView(space(7))
        val actionRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow2.addView(smallAction("+ অন্যান্য বিল", "#5B557E") { addRoomExpense(defaultKind = FinanceStore.KIND_ELECTRICITY) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionRow2.addView(hSpace(7))
        actionRow2.addView(smallAction("🗂 History", "#385B86") { showRoomMessHistory() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actionRow2)

        // Member list is intentionally collapsed by default so the finance page
        // stays compact. The state is remembered locally and backed up with the
        // rest of Guide UI preferences where applicable.
        root.addView(space(20))
        val membersExpanded = uiPrefs.getBoolean("room_members_expanded_v326", false)
        val memberDrop = card("#17243D", padding = 13)
        val memberDropRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val memberDropLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        memberDropLabels.addView(text("সদস্য ও Payment", 15.5f, "#FFFFFF", bold = true))
        memberDropLabels.addView(text("Active ${activeMembers.size} • মোট ${members.size} member", 10.8f, "#8696BB"))
        memberDropRow.addView(memberDropLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        memberDropRow.addView(text(if (membersExpanded) "▲" else "▼", 16f, "#AFA4FF", bold = true))
        memberDrop.addView(memberDropRow)
        memberDrop.setOnClickListener {
            uiPrefs.edit().putBoolean("room_members_expanded_v326", !membersExpanded).apply()
            render()
        }
        root.addView(memberDrop)

        if (membersExpanded) {
            root.addView(space(8))
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
        }

        fun addActiveExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>) {
            root.addView(space(22))
            root.addView(text(titleText, 16f, "#B8C4DF", bold = true))
            val t = total(items)
            val p = paid(items)
            val d = due(items)
            root.addView(text(if (items.isEmpty()) "$subtitleText\nএখন কোনো চলমান হিসাব নেই" else "$subtitleText\nমোট ${moneyText(t)} • Paid ${moneyText(p)} • Pending ${moneyText(d)}", 11f, if (items.isEmpty()) "#7F8EAE" else "#8291B4", bold = items.isNotEmpty()).apply { setPadding(0, dp(3), 0, dp(8)) })
            if (items.isEmpty()) {
                root.addView(emptyCard("এখন কোনো চলমান হিসাব নেই", "নতুন হিসাব যোগ করলে এখানে দেখা যাবে। Complete হলে History-তে চলে যাবে।"))
                return
            }
            items.sortedByDescending { it.date }.take(20).forEachIndexed { expenseIndex, item ->
                val collection = financeRoom.expenseCollection(item)
                val participantMembers = item.participantIds.mapNotNull { id -> allRoomMembers.firstOrNull { it.id == id } }
                val c = card("#191F34", padding = 13)
                val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                left.addView(text(item.title, 16f, "#FFFFFF", bold = true))
                left.addView(text("মোট ${moneyText(item.amount)} • ${participantMembers.size} জন • জনপ্রতি ${moneyText(item.shareAmount())}", 11.5f, "#E4BC7E").apply { setPadding(0, dp(3), 0, 0) })
                left.addView(text("Paid ${moneyText(collection.second)} • Pending ${moneyText(collection.third)}", 10.8f, "#FFAAAE", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                header.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(text("PENDING", 12f, "#F0C17A", bold = true))
                c.addView(header)
                c.addView(space(8))
                participantMembers.forEach { member ->
                    val memberPaid = financeRoom.paidForExpense(item, member.id)
                    val memberDue = financeRoom.remainingForExpense(item, member.id)
                    val memberLine = when {
                        memberDue < 0.005 -> "✓ ${member.name} • PAYMENT COMPLETE • ${moneyText(memberPaid)}"
                        memberPaid > 0.005 -> "◐ ${member.name} • Paid ${moneyText(memberPaid)} • বাকি ${moneyText(memberDue)}"
                        else -> "⏳ ${member.name} • দিতে হবে ${moneyText(memberDue)}"
                    }
                    c.addView(text(memberLine, 11.2f, if (memberDue < 0.005) "#70D7B0" else if (memberPaid > 0.005) "#F0C17A" else "#FF9EA4", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                }
                c.addView(text(roomHistoryFullDate(item.date), 10.4f, "#7382A4").apply { setPadding(0, dp(7), 0, 0) })
                c.setOnClickListener { roomExpenseActions(item) }
                root.addView(c)
                if (expenseIndex < items.lastIndex) root.addView(space(8))
            }
        }

        addActiveExpenseSection("🏠 Room Rent", "শুধু Pending/Partial Rent এখানে থাকবে।", activeRent)
        addActiveExpenseSection("🛒 Mess / বাজার", "শুধু Pending/Partial Mess খরচ এখানে থাকবে।", activeMess)
        if (activeOther.isNotEmpty()) addActiveExpenseSection("▣ অন্যান্য Shared Bill", "বিদ্যুৎ, ইন্টারনেট, গ্যাস, পানি ও অন্যান্য চলমান বিল।", activeOther)

        root.addView(space(22))
        val historyHero = card("#172641", padding = 14)
        val hRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val hLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hLabels.addView(text("🗂 সম্পন্ন হিসাব History", 16f, "#FFFFFF", bold = true))
        hLabels.addView(text("${completedExpenses.size}টি সম্পূর্ণ হিসাব নিরাপদে সংরক্ষিত", 11f, "#92A1C5").apply { setPadding(0, dp(3), 0, 0) })
        if (completedExpenses.isNotEmpty()) {
            val lastDone = completedExpenses.maxByOrNull { it.date }
            hLabels.addView(text("সর্বশেষ: ${lastDone?.let { roomHistoryFullDate(it.date) } ?: "-"}", 10.5f, "#70D5B0").apply { setPadding(0, dp(3), 0, 0) })
        }
        hRow.addView(hLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hRow.addView(text("দেখুন  ›", 12f, "#B8AEFF", bold = true))
        historyHero.addView(hRow)
        historyHero.setOnClickListener { showRoomMessHistory() }
        root.addView(historyHero)

'''
    money = money[:room_start] + room_tail + money[return_pos:]
    ms = ms[:start] + money + ms[end:]

    # History helpers. These use computed completion instead of duplicating a
    # separate archive database, so Firebase backup/restore remains compatible.
    helper_anchor = '    private fun financePremiumActionRow('
    require(ms, helper_anchor, 'finance premium helper anchor')
    history_helpers = r'''    private fun roomHistoryMonthLabel(monthKey: String): String {
        return runCatching {
            java.time.YearMonth.parse(monthKey).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        }.getOrElse { monthKey }
    }

    private fun roomHistoryFullDate(date: String): String {
        return runCatching {
            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))
        }.getOrElse { date }
    }

    private fun showRoomMessHistory() {
        val finance = FinanceStore(this)
        val expenses = finance.roomExpenses().filter {
            it.amount > 0.005 && finance.expenseCollection(it).third <= 0.005
        }
        if (expenses.isEmpty()) {
            Toast.makeText(this, "এখনও কোনো সম্পূর্ণ Room/Mess হিসাব History-তে নেই", Toast.LENGTH_LONG).show()
            return
        }
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = premiumGradientStroke("#111C38", "#6D66E8", 1, 24)
        }
        box.addView(text("🗂 Room / Mess History", 21f, "#FFFFFF", bold = true))
        box.addView(text("Complete হওয়া Rent/Mess এখানে স্থায়ীভাবে থাকবে • মাস অনুযায়ী PDF পাঠাতে পারবেন", 11f, "#9DAACB").apply { setPadding(0, dp(4), 0, dp(12)) })

        val allMembers = finance.roomMembers()
        val grouped = expenses.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it })
        grouped.forEach { (monthKey, monthItems) ->
            val monthPayments = monthItems.flatMap { finance.paymentsForExpense(it.id) }
            val monthTotal = monthItems.sumOf { it.amount }
            val monthPaid = monthItems.sumOf { finance.expenseCollection(it).second }
            val monthCard = card("#172746", padding = 13)
            monthCard.addView(text(roomHistoryMonthLabel(monthKey), 18f, "#FFFFFF", bold = true))
            val firstDate = monthItems.minOfOrNull { it.date } ?: monthKey
            val lastDate = monthPayments.maxOfOrNull { it.date } ?: (monthItems.maxOfOrNull { it.date } ?: monthKey)
            monthCard.addView(text("${roomHistoryFullDate(firstDate)} — ${roomHistoryFullDate(lastDate)}", 10.5f, "#8FA0C8").apply { setPadding(0, dp(3), 0, 0) })
            monthCard.addView(text("মোট ${moneyText(monthTotal)} • Paid ${moneyText(monthPaid)} • ✓ COMPLETE", 12.5f, "#70D7B0", bold = true).apply { setPadding(0, dp(6), 0, dp(8)) })

            monthItems.sortedByDescending { it.date }.forEach { expense ->
                val expensePayments = finance.paymentsForExpense(expense.id).sortedBy { it.date }
                val itemBox = card("#1B263D", padding = 11)
                itemBox.addView(text("${finance.kindLabel(expense.kind)} • ${expense.title}", 14f, "#FFFFFF", bold = true))
                itemBox.addView(text("${moneyText(expense.amount)} • ${expense.participantIds.size} জন • জনপ্রতি ${moneyText(expense.shareAmount())}", 10.8f, "#E6BF82").apply { setPadding(0, dp(3), 0, 0) })
                val completedDate = expensePayments.maxOfOrNull { it.date } ?: expense.date
                itemBox.addView(text("✓ Payment completed • ${roomHistoryFullDate(completedDate)}", 10.8f, "#70D7B0", bold = true).apply { setPadding(0, dp(4), 0, dp(5)) })

                expense.participantIds.forEach { memberId ->
                    val member = allMembers.firstOrNull { it.id == memberId }
                    val memberName = member?.name ?: "Member"
                    val memberPaid = finance.paidForExpense(expense, memberId)
                    itemBox.addView(text("✓ $memberName • Paid ${moneyText(memberPaid)} • Share ${moneyText(expense.shareAmount())}", 10.8f, "#B9C6E2", bold = true).apply { setPadding(0, dp(2), 0, 0) })
                }
                if (expensePayments.isNotEmpty()) {
                    itemBox.addView(text("Payment activity", 10.5f, "#8E9FC7", bold = true).apply { setPadding(0, dp(7), 0, dp(2)) })
                    expensePayments.forEach { payment ->
                        val payer = allMembers.firstOrNull { it.id == payment.memberId }?.name ?: "Member"
                        itemBox.addView(text("• $payer • ${moneyText(payment.amount)} • ${roomHistoryFullDate(payment.date)}${if (payment.note.isNotBlank()) " • ${payment.note}" else ""}", 9.8f, "#8998BA"))
                    }
                }
                monthCard.addView(itemBox)
                monthCard.addView(space(7))
            }

            monthCard.addView(pillButton("📄 ${roomHistoryMonthLabel(monthKey)} PDF / WhatsApp", "#28765E") {
                dialog.dismiss()
                shareRoomMessHistoryPdf(monthKey)
            })
            box.addView(monthCard)
            box.addView(space(10))
        }

        val close = Button(this).apply {
            text = "বন্ধ"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = premiumGradientStroke("#293450", "#5B6D9D", 1, 14)
            setOnClickListener { dialog.dismiss() }
        }
        box.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.90f).toInt())
        }
        dialog.show()
    }

    private fun shareRoomMessHistoryPdf(monthKey: String) {
        runCatching {
            val dir = java.io.File(cacheDir, "shared_reports").apply { mkdirs() }
            val safeMonth = monthKey.replace("/", "-")
            val file = java.io.File(dir, "Guide-Room-Mess-History-$safeMonth.pdf")
            GuidePdfReport.writeRoomMessMonthFile(this, store, file, monthKey)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "${roomHistoryMonthLabel(monthKey)} Room/Mess হিসাব")
                putExtra(android.content.Intent.EXTRA_TEXT, "${roomHistoryMonthLabel(monthKey)} সম্পূর্ণ Room/Mess payment history")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, "History PDF WhatsApp / অন্য অ্যাপে শেয়ার করুন"))
        }.onFailure {
            Toast.makeText(this, "History PDF share করা যায়নি: ${it.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

'''
    ms = ms.replace(helper_anchor, history_helpers + helper_anchor, 1)

    # The premium member payment menu now works on every currently pending bill,
    # not only bills whose date happens to be in the current calendar month.
    complete_fn = r'''    private fun completeMemberMonthPayment(member: RoomMember) {
        val finance = FinanceStore(this)
        val pending = finance.roomExpenses().filter {
            member.id in it.participantIds && finance.remainingForExpense(it, member.id) > 0.005
        }
        if (pending.isEmpty()) {
            Toast.makeText(this, "✓ ${member.name}-এর বর্তমান সব payment complete", Toast.LENGTH_LONG).show()
            return
        }
        val totalDue = pending.sumOf { finance.remainingForExpense(it, member.id) }
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#102B2A", "#58D7AC", 1, 24)
        }
        box.addView(text("✓ বর্তমান Payment Complete", 20f, "#FFFFFF", bold = true))
        box.addView(text(member.name, 14f, "#8FE0C3", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        box.addView(text("মোট বাকি ${moneyText(totalDue)}", 25f, "#F2C77F", bold = true).apply { setPadding(0, dp(12), 0, dp(10)) })
        pending.sortedBy { it.date }.forEach { expense ->
            val due = finance.remainingForExpense(expense, member.id)
            val line = card("#173A37", padding = 11)
            line.addView(text(expense.title, 13.5f, "#FFFFFF", bold = true))
            line.addView(text("${finance.kindLabel(expense.kind)} • ${roomHistoryFullDate(expense.date)} • বাকি ${moneyText(due)}", 11f, "#9CCFBE").apply { setPadding(0, dp(3), 0, 0) })
            box.addView(line)
            box.addView(space(6))
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(smallAction("বাতিল", "#39465F") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(hSpace(8))
        actions.addView(smallAction("✓ সব Complete", "#2F8D72") {
            val payments = finance.roomExpensePayments()
            pending.forEach { expense ->
                val remaining = finance.remainingForExpense(expense, member.id)
                if (remaining > 0.005) payments.add(RoomExpensePayment(expenseId = expense.id, memberId = member.id, toMemberId = "", amount = remaining, date = store.today(), note = "Payment complete"))
            }
            finance.saveRoomExpensePayments(payments)
            CloudSyncManager.scheduleUpload(this)
            dialog.dismiss()
            Toast.makeText(this, "✓ ${member.name} • ${moneyText(totalDue)} PAYMENT COMPLETE • History আপডেট হয়েছে", Toast.LENGTH_LONG).show()
            render()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(actions)
        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

'''
    ms = replace_function(ms, '    private fun completeMemberMonthPayment(member: RoomMember) {', complete_fn)

    choose_fn = r'''    private fun chooseMemberPartialPayment(member: RoomMember) {
        val finance = FinanceStore(this)
        val pending = finance.roomExpenses().filter {
            member.id in it.participantIds && finance.remainingForExpense(it, member.id) > 0.005
        }
        if (pending.isEmpty()) {
            Toast.makeText(this, "✓ ${member.name}-এর কোনো pending payment নেই", Toast.LENGTH_LONG).show()
            return
        }
        if (pending.size == 1) {
            editMemberPaymentAmount(member, pending.first())
            return
        }
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#141D38", "#6E7FFF", 1, 24)
        }
        box.addView(text("✎ Payment amount", 20f, "#FFFFFF", bold = true))
        box.addView(text("${member.name} • কোন চলমান হিসাবের payment দেবেন?", 11.5f, "#9EADD0").apply { setPadding(0, dp(4), 0, dp(12)) })
        pending.sortedByDescending { it.date }.forEach { expense ->
            val due = finance.remainingForExpense(expense, member.id)
            box.addView(financePremiumActionRow("৳", expense.title, "${finance.kindLabel(expense.kind)} • ${roomHistoryFullDate(expense.date)} • বাকি ${moneyText(due)}", "#263A61") {
                dialog.dismiss()
                editMemberPaymentAmount(member, expense)
            })
            box.addView(space(7))
        }
        val close = Button(this).apply {
            text = "বন্ধ"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = premiumGradientStroke("#2A3553", "#55688E", 1, 13)
            setOnClickListener { dialog.dismiss() }
        }
        box.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

'''
    ms = replace_function(ms, '    private fun chooseMemberPartialPayment(member: RoomMember) {', choose_fn)
    ms = ms.replace('"এই মাসের সব বাকি Complete"', '"বর্তমান সব বাকি Complete"')
    ms = ms.replace('"সব pending share একসাথে সম্পূর্ণ পরিশোধ"', '"সব চলমান pending share একসাথে সম্পূর্ণ পরিশোধ"')

    mp.write_text(ms)
    print('v3.26 active-cycle dashboard, dropdown members and history UI applied')
else:
    print('v3.26 MainActivity patch already applied')


# Per-month completed-history PDF that can be sent directly from History.
pp = Path('app/src/main/java/com/guide/app/PdfReportsActivity.kt')
ps = pp.read_text()
if 'writeRoomMessMonthFile' not in ps:
    anchor = '    fun writeRoomMessFile(context: android.content.Context, store: GuideStore, file: java.io.File) {'
    require(ps, anchor, 'writeRoomMessFile anchor')
    month_pdf = r'''    fun writeRoomMessMonthFile(context: android.content.Context, store: GuideStore, file: java.io.File, monthKey: String) {
        val doc = PdfDocument()
        val writer = PdfWriter(doc, store.profileName())
        writeRoomMessMonth(writer, context, monthKey)
        writer.finish()
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun writeRoomMessMonth(w: PdfWriter, context: android.content.Context, monthKey: String) {
        val finance = FinanceStore(context)
        val currency = context.getSharedPreferences("guide_ui", android.content.Context.MODE_PRIVATE).getString("currency", "SAR") ?: "SAR"
        val allMembers = finance.roomMembers()
        val expenses = finance.roomExpenses().filter {
            it.date.startsWith(monthKey) && it.amount > 0.005 && finance.expenseCollection(it).third <= 0.005
        }
        val monthName = runCatching {
            java.time.YearMonth.parse(monthKey).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        }.getOrElse { monthKey }

        w.reportHero(
            "ROOM / MESS PAYMENT HISTORY",
            "$monthName • সম্পূর্ণ হিসাব",
            "Completed payment archive • Generated ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a", Locale.ENGLISH))}"
        )
        if (expenses.isEmpty()) {
            w.info("এই মাসে কোনো সম্পূর্ণ Room/Mess হিসাব নেই।")
            return
        }

        val rent = expenses.filter { it.kind == FinanceStore.KIND_RENT }
        val mess = expenses.filter { it.kind == FinanceStore.KIND_MARKET }
        val other = expenses.filter { it.kind != FinanceStore.KIND_RENT && it.kind != FinanceStore.KIND_MARKET }
        fun total(items: List<RoomExpense>) = items.sumOf { it.amount }
        fun paid(items: List<RoomExpense>) = items.sumOf { finance.expenseCollection(it).second }

        w.monthBanner(monthName, true)
        w.summaryCard("ROOM RENT", currency, total(rent), paid(rent), 0.0)
        w.summaryCard("MESS / MARKET", currency, total(mess), paid(mess), 0.0)
        if (other.isNotEmpty()) w.summaryCard("OTHER SHARED BILL", currency, total(other), paid(other), 0.0)
        w.rule()

        expenses.sortedBy { it.date }.forEach { expense ->
            val paymentRows = finance.paymentsForExpense(expense.id).sortedBy { it.date }
            val completedDate = paymentRows.maxOfOrNull { it.date } ?: expense.date
            val fullDate = runCatching {
                LocalDate.parse(completedDate).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))
            }.getOrElse { completedDate }
            w.expenseCard(
                "${finance.kindLabel(expense.kind)} • ${expense.title}",
                "Total $currency ${money(expense.amount)} • ${expense.participantIds.size} member • Share $currency ${money(expense.shareAmount())}",
                "✓ PAYMENT COMPLETE • $fullDate",
                true
            )
            expense.participantIds.forEach { memberId ->
                val name = allMembers.firstOrNull { it.id == memberId }?.name ?: "Member"
                val memberPaid = finance.paidForExpense(expense, memberId)
                w.memberPaymentCard(
                    name,
                    "Share $currency ${money(expense.shareAmount())} • Paid $currency ${money(memberPaid)}",
                    "✓ COMPLETE",
                    true
                )
            }
            if (paymentRows.isNotEmpty()) {
                w.info("Payment activity")
                paymentRows.forEach { payment ->
                    val name = allMembers.firstOrNull { it.id == payment.memberId }?.name ?: "Member"
                    val paymentDate = runCatching {
                        LocalDate.parse(payment.date).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))
                    }.getOrElse { payment.date }
                    w.row("", "$name • $currency ${money(payment.amount)} • $paymentDate${if (payment.note.isNotBlank()) " • ${payment.note}" else ""}")
                }
            }
            w.sectionBreak()
        }
    }

'''
    ps = ps.replace(anchor, month_pdf + anchor, 1)
    pp.write_text(ps)
    print('v3.26 per-month Room/Mess history PDF applied')
else:
    print('v3.26 month PDF already applied')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 39', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.26.0"', gs, count=1)
gp.write_text(gs)
print('v3.26 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.26.0"', cs, count=1)
cp.write_text(cs)
print('v3.26 cloud metadata applied')
