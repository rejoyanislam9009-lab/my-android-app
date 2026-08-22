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
# Persist deleted Room members as hidden/archived records. This gives the user
# a real Delete action without destroying names referenced by older expenses.
# ---------------------------------------------------------------------------
fp = Path('app/src/main/java/com/guide/app/FinanceStore.kt')
fs = fp.read_text()
if 'val deleted: Boolean = false' not in fs:
    old_member = '''data class RoomMember(\n    val id: String = UUID.randomUUID().toString(),\n    val name: String,\n    val active: Boolean = true\n)'''
    new_member = '''data class RoomMember(\n    val id: String = UUID.randomUUID().toString(),\n    val name: String,\n    val active: Boolean = true,\n    val deleted: Boolean = false\n)'''
    require(fs, old_member, 'RoomMember model')
    fs = fs.replace(old_member, new_member, 1)

    old_parse = '''                name = o.optString("name", "Member"),\n                active = o.optBoolean("active", true)\n            )'''
    new_parse = '''                name = o.optString("name", "Member"),\n                active = o.optBoolean("active", true),\n                deleted = o.optBoolean("deleted", false)\n            )'''
    require(fs, old_parse, 'RoomMember parse')
    fs = fs.replace(old_parse, new_parse, 1)

    old_save = '''            put("id", item.id); put("name", item.name); put("active", item.active)\n        }) }'''
    new_save = '''            put("id", item.id); put("name", item.name); put("active", item.active); put("deleted", item.deleted)\n        }) }'''
    require(fs, old_save, 'RoomMember save')
    fs = fs.replace(old_save, new_save, 1)
    fp.write_text(fs)
    print('v3.23 archived member delete model applied')
else:
    print('v3.23 archived member model already present')


mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideRoomMessSeparatedV323' not in ms:
    # Let quick-action buttons open the shared-expense form on a requested type.
    require(ms, '    private fun addRoomExpense(existing: RoomExpense? = null) {', 'addRoomExpense signature')
    ms = ms.replace(
        '    private fun addRoomExpense(existing: RoomExpense? = null) {',
        '    private fun addRoomExpense(existing: RoomExpense? = null, defaultKind: String? = null) {',
        1,
    )
    require(ms, 'existing?.kind ?: FinanceStore.KIND_MARKET', 'default expense kind')
    ms = ms.replace('existing?.kind ?: FinanceStore.KIND_MARKET', 'existing?.kind ?: defaultKind ?: FinanceStore.KIND_MARKET', 1)

    # Replace only the v3.22 Room/Mess portion of moneyPage. Personal daily
    # accounts and named receivable/payable sections above it remain unchanged.
    bounds = function_bounds(ms, '    private fun moneyPage(): LinearLayout {')
    if not bounds:
        raise SystemExit('moneyPage not found')
    start, end = bounds
    money = ms[start:end]
    marker = '        // GuideRoomMessPaymentsV322\n'
    require(money, marker, 'v3.22 Room/Mess marker')
    room_start = money.index(marker)
    return_marker = '        return root\n'
    return_pos = money.rfind(return_marker)
    if return_pos < room_start:
        raise SystemExit('moneyPage return marker not found')

    room_tail = r'''        // GuideRoomMessSeparatedV323
        val financeRoom = finance
        val allRoomMembers = financeRoom.roomMembers()
        val members = allRoomMembers.filter { !it.deleted }
        val activeMembers = members.filter { it.active }
        val allExpenses = financeRoom.roomExpenses()
        val allPayments = financeRoom.roomExpensePayments()
        val monthKey = LocalDate.now().toString().substring(0, 7)
        val monthExpenses = allExpenses.filter { it.date.startsWith(monthKey) }
        val rentExpenses = allExpenses.filter { it.kind == FinanceStore.KIND_RENT }
        val messExpenses = allExpenses.filter { it.kind == FinanceStore.KIND_MARKET }
        val otherExpenses = allExpenses.filter { it.kind != FinanceStore.KIND_RENT && it.kind != FinanceStore.KIND_MARKET }
        val monthRent = monthExpenses.filter { it.kind == FinanceStore.KIND_RENT }
        val monthMess = monthExpenses.filter { it.kind == FinanceStore.KIND_MARKET }
        val monthOther = monthExpenses.filter { it.kind != FinanceStore.KIND_RENT && it.kind != FinanceStore.KIND_MARKET }

        fun collectionDue(items: List<RoomExpense>): Double = items.sumOf { financeRoom.expenseCollection(it).third }
        fun collectionPaid(items: List<RoomExpense>): Double = items.sumOf { financeRoom.expenseCollection(it).second }

        root.addView(space(22))
        root.addView(sectionTitle("Room / Mess হিসাব"))
        root.addView(text("রুম ভাড়া, Mess/বাজার এবং অন্যান্য বিল এখন আলাদা আলাদা হিসাব।", 11.5f, "#8492B6").apply { setPadding(0, dp(3), 0, dp(10)) })

        val overview = card("#1B263B")
        overview.addView(text("এই মাসের সারাংশ", 12f, "#98A5C6", bold = true))
        overview.addView(text("🏠 রুম ভাড়া  ${moneyText(monthRent.sumOf { it.amount })}", 18f, "#F0C17A", bold = true).apply { setPadding(0, dp(7), 0, 0) })
        overview.addView(text("🛒 Mess / বাজার  ${moneyText(monthMess.sumOf { it.amount })}", 17f, "#72D1B0", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        if (monthOther.isNotEmpty()) overview.addView(text("💡 অন্যান্য বিল  ${moneyText(monthOther.sumOf { it.amount })}", 15f, "#AEB8D2", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        overview.addView(text("Active member ${activeMembers.size} জন", 11.5f, "#91A0C1", bold = true).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(overview)
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
        actionRow2.addView(smallAction("✓ Member payment", "#318B76") { recordAnyRoomExpensePayment() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actionRow2)

        root.addView(space(20))
        root.addView(text("সদস্য", 15f, "#B4C0DD", bold = true))
        root.addView(text("এখানে আর ‘পাবে’ দেখানো হবে না—কে কত cash দিয়েছে, নিজের share কত, আর কত বাকি সেটাই দেখাবে।", 10.8f, "#7787AA").apply { setPadding(0, dp(3), 0, dp(8)) })
        if (members.isEmpty()) {
            root.addView(emptyCard("কোনো Room/Mess member নেই", "নাম দিয়ে সদস্য যোগ করুন। Member card ট্যাপ করলে Edit, Inactive এবং Delete পাওয়া যাবে।"))
        } else {
            val monthExpenseIds = monthExpenses.map { it.id }.toSet()
            members.forEachIndexed { index, member ->
                val memberMonthExpenses = monthExpenses.filter { member.id in it.participantIds }
                val ownShare = memberMonthExpenses.sumOf { it.shareAmount() }
                val ownDue = memberMonthExpenses.sumOf { financeRoom.remainingForExpense(it, member.id) }
                val paidUpfront = monthExpenses.filter { it.paidById == member.id }.sumOf { it.amount }
                val laterPayments = allPayments.filter { it.memberId == member.id && it.expenseId in monthExpenseIds }.sumOf { it.amount }
                val cashPaid = paidUpfront + laterPayments
                val memberStatus = when {
                    ownShare < 0.005 -> "এই মাসে কোনো share নেই"
                    ownDue < 0.005 -> "✓ PAYMENT COMPLETE"
                    else -> "⏳ বাকি দিতে হবে ${moneyText(ownDue)}"
                }
                val c = card(if (ownShare > 0.005 && ownDue < 0.005) "#162B2B" else "#151F37", padding = 13)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(member.name, 16f, if (member.active) "#FFFFFF" else "#8995AF", bold = true))
                labels.addView(text(if (member.active) "● Active member" else "○ Inactive member", 10.7f, if (member.active) "#69CBAA" else "#7381A2"))
                labels.addView(text("দিয়েছেন ${moneyText(cashPaid)} • নিজের মোট share ${moneyText(ownShare)}", 11f, "#99A6C5").apply { setPadding(0, dp(4), 0, 0) })
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text(memberStatus, 12.5f, if (ownShare > 0.005 && ownDue < 0.005) "#70D7B0" else if (ownDue > 0.005) "#FF9FA4" else "#94A2C0", bold = true))
                c.addView(row)
                c.setOnClickListener { roomMemberActions(member) }
                root.addView(c)
                if (index < members.lastIndex) root.addView(space(7))
            }
        }

        fun addExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>, monthItems: List<RoomExpense>) {
            root.addView(space(22))
            root.addView(text(titleText, 16f, "#B8C4DF", bold = true))
            val monthTotal = monthItems.sumOf { it.amount }
            val monthDue = collectionDue(monthItems)
            val monthPaid = collectionPaid(monthItems)
            val statusLine = when {
                monthTotal < 0.005 -> "এই মাসে কোনো হিসাব নেই"
                monthDue < 0.005 -> "✓ এই মাসের পেমেন্ট সম্পূর্ণ পরিশোধ হয়েছে"
                else -> "সদস্যরা দিয়েছে ${moneyText(monthPaid)} • বাকি সংগ্রহ ${moneyText(monthDue)}"
            }
            root.addView(text("$subtitleText\n$statusLine", 11f, if (monthTotal > 0.005 && monthDue < 0.005) "#6ED6AF" else "#8291B4", bold = monthTotal > 0.005).apply { setPadding(0, dp(3), 0, dp(8)) })
            if (items.isEmpty()) {
                root.addView(emptyCard("এখনও কোনো হিসাব নেই", subtitleText))
                return
            }
            items.take(12).forEachIndexed { expenseIndex, item ->
                val collection = financeRoom.expenseCollection(item)
                val fullyPaid = collection.third < 0.005
                val participantMembers = item.participantIds.mapNotNull { id -> allRoomMembers.firstOrNull { it.id == id } }
                val paidCount = participantMembers.count { financeRoom.paymentStatus(item, it.id) == "PAID" }
                val c = card(if (fullyPaid) "#162B2B" else "#191F34", padding = 13)
                val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                left.addView(text(item.title, 16f, "#FFFFFF", bold = true))
                left.addView(text("মোট ${moneyText(item.amount)} • জনপ্রতি ${moneyText(item.shareAmount())}", 11.5f, "#E4BC7E").apply { setPadding(0, dp(3), 0, 0) })
                left.addView(text("আগে পুরো টাকা দিয়েছেন: ${financeRoom.memberName(item.paidById)}", 10.8f, "#96A4C3").apply { setPadding(0, dp(3), 0, 0) })
                header.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(text(if (fullyPaid) "✓ COMPLETE" else "$paidCount/${participantMembers.size} PAID", 12f, if (fullyPaid) "#70D7B0" else "#F0C17A", bold = true))
                c.addView(header)
                c.addView(space(8))

                participantMembers.forEach { participant ->
                    val status = financeRoom.paymentStatus(item, participant.id)
                    val paid = financeRoom.paidForExpense(item, participant.id)
                    val due = financeRoom.remainingForExpense(item, participant.id)
                    val detail = when {
                        participant.id == item.paidById -> "✓ নিজের share PAYMENT COMPLETE • আগে দিয়েছেন ${moneyText(item.amount)}"
                        status == "PAID" -> "✓ PAYMENT COMPLETE • দিয়েছে ${moneyText(paid)}"
                        status == "PARTIAL" -> "◐ দিয়েছে ${moneyText(paid)} • বাকি ${moneyText(due)}"
                        else -> "⏳ দিতে হবে ${moneyText(due)}"
                    }
                    val detailColor = when {
                        status == "PAID" -> "#70D7B0"
                        status == "PARTIAL" -> "#F0C17A"
                        else -> "#FF9FA4"
                    }
                    val memberLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
                    memberLine.addView(text(participant.name, 12f, "#FFFFFF", bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    memberLine.addView(text(detail, 11f, detailColor, bold = true))
                    c.addView(memberLine)
                }

                c.addView(text(if (fullyPaid) "✓ এই খরচ সম্পূর্ণ পরিশোধ হয়েছে" else "বাকি সংগ্রহ ${moneyText(collection.third)}", 11.5f, if (fullyPaid) "#70D7B0" else "#FFAAAE", bold = true).apply { setPadding(0, dp(7), 0, 0) })
                c.addView(text("${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 10.5f, "#7483A6").apply { setPadding(0, dp(3), 0, 0) })
                c.setOnClickListener { roomExpenseActions(item) }
                root.addView(c)
                if (expenseIndex < minOf(11, items.lastIndex)) root.addView(space(8))
            }
        }

        addExpenseSection("🏠 রুম ভাড়া", "সব Active member-এর মধ্যে সমান ভাগ হবে।", rentExpenses, monthRent)
        addExpenseSection("🛒 Mess / বাজার খরচ", "বাজার বা Mess খরচে যাদের share আছে শুধু তাদের হিসাব থাকবে।", messExpenses, monthMess)
        if (otherExpenses.isNotEmpty() || monthOther.isNotEmpty()) {
            addExpenseSection("💡 অন্যান্য Shared বিল", "বিদ্যুৎ, ইন্টারনেট, গ্যাস, পানি ও অন্যান্য shared bill।", otherExpenses, monthOther)
        }

        if (allPayments.isNotEmpty()) {
            root.addView(space(22))
            root.addView(text("✓ Member payment history", 15f, "#B8C4DF", bold = true))
            root.addView(text("কে কোন খরচে কত টাকা দিয়েছে—আলাদা history।", 10.8f, "#7887AA").apply { setPadding(0, dp(3), 0, dp(7)) })
            allPayments.take(15).forEachIndexed { paymentIndex, payment ->
                val expense = allExpenses.firstOrNull { it.id == payment.expenseId }
                val stillDue = expense?.let { financeRoom.remainingForExpense(it, payment.memberId) } ?: 0.0
                val status = if (stillDue < 0.005) "✓ PAYMENT COMPLETE" else "◐ বাকি ${moneyText(stillDue)}"
                val c = card("#14283A", padding = 12)
                c.addView(text("${financeRoom.memberName(payment.memberId)} • দিয়েছে ${moneyText(payment.amount)}", 14f, "#70D7B0", bold = true))
                c.addView(text("${expense?.title ?: "Shared খরচ"} • ${status}", 11.2f, if (stillDue < 0.005) "#70D7B0" else "#F0C17A", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                c.addView(text("${friendlyDate(payment.date)}${if (payment.note.isNotBlank()) " • ${payment.note}" else ""}", 10.5f, "#7483A6").apply { setPadding(0, dp(3), 0, 0) })
                root.addView(c)
                if (paymentIndex < minOf(14, allPayments.lastIndex)) root.addView(space(7))
            }
        }

        val settlements = financeRoom.roomSettlements()
        if (settlements.isNotEmpty()) {
            root.addView(space(20))
            root.addView(pillButton("অন্যান্য balance adjustment / পুরোনো Settlement", "#4F416D") { addRoomSettlement() })
        }

'''
    new_money = money[:room_start] + room_tail + return_marker + '    }'
    ms = ms[:start] + new_money + ms[end:]

    # Member menu: include a safe Delete. Deleted members disappear from the
    # current member list but remain internally archived so historical expense
    # names and payment records stay readable and mathematically stable.
    new_member_actions = r'''    private fun roomMemberActions(item: RoomMember) {
        val options = arrayOf(
            "নাম/স্ট্যাটাস এডিট করুন",
            if (item.active) "Inactive করুন" else "Active করুন",
            "মেম্বার ডিলিট করুন"
        )
        AlertDialog.Builder(this).setTitle(item.name).setItems(options) { _, which ->
            when (which) {
                0 -> addRoomMember(item)
                1 -> {
                    val finance = FinanceStore(this)
                    val items = finance.roomMembers()
                    val index = items.indexOfFirst { it.id == item.id }
                    if (index >= 0) items[index] = item.copy(active = !item.active)
                    finance.saveRoomMembers(items)
                    CloudSyncManager.scheduleUpload(this)
                    render()
                }
                2 -> deleteRoomMember(item)
            }
        }.show()
    }

'''
    ms = replace_function(ms, '    private fun roomMemberActions(item: RoomMember) {', new_member_actions)

    delete_helper = r'''    private fun deleteRoomMember(item: RoomMember) {
        val finance = FinanceStore(this)
        val historyCount = finance.roomExpenses().count { item.id == it.paidById || item.id in it.participantIds }
        val paymentCount = finance.roomExpensePayments().count { it.memberId == item.id || it.toMemberId == item.id }
        val message = if (historyCount > 0 || paymentCount > 0) {
            "${item.name}-কে বর্তমান Member list থেকে ডিলিট করা হবে। পুরোনো ${historyCount}টি খরচ ও ${paymentCount}টি payment history নিরাপদে থাকবে, তাই আগের হিসাব নষ্ট হবে না।"
        } else {
            "${item.name}-কে Member list থেকে ডিলিট করবেন?"
        }
        AlertDialog.Builder(this)
            .setTitle("মেম্বার ডিলিট")
            .setMessage(message)
            .setPositiveButton("ডিলিট করুন") { _, _ ->
                val items = finance.roomMembers()
                val index = items.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    items[index] = item.copy(active = false, deleted = true)
                    finance.saveRoomMembers(items)
                    CloudSyncManager.scheduleUpload(this)
                    Toast.makeText(this, "${item.name} Member list থেকে ডিলিট হয়েছে", Toast.LENGTH_LONG).show()
                    render()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

'''
    anchor = '    private fun addRoomExpense(existing: RoomExpense? = null, defaultKind: String? = null) {'
    require(ms, anchor, 'v3.23 addRoomExpense anchor')
    ms = ms.replace(anchor, delete_helper + anchor, 1)

    # Clearer payment detail wording.
    ms = ms.replace('"মূল payer • নিজের share পরিশোধিত"', '"আগে পুরো টাকা দিয়েছেন • নিজের share PAYMENT COMPLETE"')
    ms = ms.replace('"Paid ${moneyText(paid)} • Due ${moneyText(due)}"', '"দিয়েছেন ${moneyText(paid)} • বাকি ${moneyText(due)}"')
    ms = ms.replace('"✓ PAID"', '"✓ PAYMENT COMPLETE"')

    # Replace the payment save flow so a completed member payment and an entire
    # completed expense both produce an explicit success message.
    new_payment_for_member = r'''    private fun recordRoomExpensePaymentForMember(item: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val remaining = finance.remainingForExpense(item, member.id)
        if (remaining < 0.005) {
            Toast.makeText(this, "✓ ${member.name} • PAYMENT COMPLETE", Toast.LENGTH_SHORT).show()
            return
        }
        val box = formBox()
        box.addView(text("${member.name} → ${finance.memberName(item.paidById)}", 15f, "#FFFFFF", bold = true))
        box.addView(text("${item.title} • দিতে হবে ${moneyText(remaining)}", 11.5f, "#F0C17A").apply { setPadding(0, dp(4), 0, dp(8)) })
        val amount = input("কত টাকা দিয়েছে").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", remaining))
        }
        val date = input("তারিখ (YYYY-MM-DD)").apply { setText(store.today()) }
        val note = input("নোট (ঐচ্ছিক)")
        box.addView(amount); box.addView(space(8)); box.addView(date); box.addView(space(8)); box.addView(note)
        AlertDialog.Builder(this).setTitle("Member payment")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("পেমেন্ট সেভ") { _, _ ->
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (value <= 0.0 || value > remaining + 0.005) {
                    Toast.makeText(this, "সর্বোচ্চ ${moneyText(remaining)} পর্যন্ত সেভ করা যাবে", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val payments = finance.roomExpensePayments()
                payments.add(RoomExpensePayment(
                    expenseId = item.id,
                    memberId = member.id,
                    toMemberId = item.paidById,
                    amount = value,
                    date = date.text.toString().trim().ifBlank { store.today() },
                    note = note.text.toString().trim()
                ))
                finance.saveRoomExpensePayments(payments)
                CloudSyncManager.scheduleUpload(this)

                val memberLeft = finance.remainingForExpense(item, member.id)
                val expenseLeft = finance.expenseCollection(item).third
                val message = when {
                    expenseLeft < 0.005 -> "✓ ${item.title} • সব সদস্যের PAYMENT COMPLETE • সম্পূর্ণ পরিশোধ হয়েছে"
                    memberLeft < 0.005 -> "✓ ${member.name} • PAYMENT COMPLETE • ${moneyText(value)} দিয়েছে"
                    else -> "${member.name} • PARTIAL PAYMENT ${moneyText(value)} • বাকি ${moneyText(memberLeft)}"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

'''
    ms = replace_function(ms, '    private fun recordRoomExpensePaymentForMember(item: RoomExpense, member: RoomMember) {', new_payment_for_member)

    mp.write_text(ms)
    print('v3.23 separated Room Rent, Mess and member payment UI applied')
else:
    print('v3.23 MainActivity patch already applied')

# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
if 'versionName = "3.22.0"' in bs:
    bs = bs.replace('versionCode = 35', 'versionCode = 36', 1)
    bs = bs.replace('versionName = "3.22.0"', 'versionName = "3.23.0"', 1)
    bp.write_text(bs)
    print('v3.23 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
updated = re.sub(r'("appVersion"\s+to\s+")[0-9.]+("\s*\))', r'\g<1>3.23.0\2', cs, count=1)
if updated != cs:
    cp.write_text(updated)
    print('v3.23 cloud metadata applied')
