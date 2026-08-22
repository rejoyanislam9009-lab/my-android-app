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


mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideRoomMessPaymentsV322' not in ms:
    # -----------------------------------------------------------------------
    # Keep the v3.21 personal/debt UI, but replace the Room/Mess tail with a
    # clearer rent/market dashboard and payment-state presentation.
    # -----------------------------------------------------------------------
    bounds = function_bounds(ms, '    private fun moneyPage(): LinearLayout {')
    if not bounds:
        raise SystemExit('moneyPage not found')
    start, end = bounds
    money = ms[start:end]
    room_marker = '        root.addView(space(22))\n        root.addView(sectionTitle("Room / Mess হিসাব"))\n'
    require(money, room_marker, 'Room/Mess section marker')
    room_start = money.index(room_marker)
    return_marker = '        return root\n'
    return_pos = money.rfind(return_marker)
    if return_pos < room_start:
        raise SystemExit('moneyPage return marker not found after Room/Mess section')

    room_tail = r'''        // GuideRoomMessPaymentsV322
        root.addView(space(22))
        root.addView(sectionTitle("Room / Mess হিসাব"))

        // One-time correction for v3.21 current-month rent records. v3.21
        // allowed a member to be accidentally omitted from rent splitting.
        if (!uiPrefs.getBoolean("room_rent_migrated_v322", false)) {
            val activeIdsForMigration = finance.roomMembers().filter { it.active }.map { it.id }.toSet()
            if (activeIdsForMigration.isNotEmpty()) {
                val currentMonth = LocalDate.now().toString().substring(0, 7)
                val migrationItems = finance.roomExpenses()
                var changed = false
                migrationItems.indices.forEach { index ->
                    val item = migrationItems[index]
                    if (item.kind == FinanceStore.KIND_RENT && item.date.startsWith(currentMonth) &&
                        finance.paymentsForExpense(item.id).isEmpty() && item.participantIds != activeIdsForMigration) {
                        migrationItems[index] = item.copy(participantIds = activeIdsForMigration)
                        changed = true
                    }
                }
                if (changed) {
                    finance.saveRoomExpenses(migrationItems)
                    CloudSyncManager.scheduleUpload(this)
                }
            }
            uiPrefs.edit().putBoolean("room_rent_migrated_v322", true).apply()
        }

        val members = finance.roomMembers()
        val activeMembers = members.filter { it.active }
        val balances = finance.roomBalances()
        val expenses = finance.roomExpenses()
        val monthKey = LocalDate.now().toString().substring(0, 7)
        val monthExpenses = expenses.filter { it.date.startsWith(monthKey) }
        val monthCollected = monthExpenses.sumOf { finance.expenseCollection(it).second }
        val monthDue = monthExpenses.sumOf { finance.expenseCollection(it).third }

        val roomHero = card("#1B263B")
        roomHero.addView(text("এই মাসের Room/Mess খরচ", 12f, "#98A5C6", bold = true))
        roomHero.addView(text(moneyText(monthExpenses.sumOf { it.amount }), 27f, "#F0C17A", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        roomHero.addView(text("Active member ${activeMembers.size} জন", 12f, "#9DAACA", bold = true).apply { setPadding(0, dp(7), 0, 0) })
        roomHero.addView(text("পেমেন্ট এসেছে ${moneyText(monthCollected)}  •  বাকি ${moneyText(monthDue)}", 12.5f, if (monthDue < 0.005) "#70D7B0" else "#FFAAAE", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        roomHero.addView(text("রুম ভাড়া = সব Active member-এর মধ্যে অটো সমান ভাগ • বাজার/অন্যান্য = প্রয়োজনমতো সদস্য বাছাই", 11f, "#8795B8").apply { setPadding(0, dp(7), 0, 0) })
        root.addView(roomHero)
        root.addView(space(10))

        val roomActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        roomActions.addView(smallAction("+ সদস্য", "#425B86") { addRoomMember() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        roomActions.addView(hSpace(7))
        roomActions.addView(smallAction("+ খরচ", "#8A5C36") { addRoomExpense() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        roomActions.addView(hSpace(7))
        roomActions.addView(smallAction("✓ পেমেন্ট", "#2E7C70") { recordAnyRoomExpensePayment() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(roomActions)
        root.addView(space(8))
        root.addView(pillButton("অন্যান্য balance Settlement", "#4F416D") { addRoomSettlement() })

        root.addView(space(18))
        root.addView(text("সদস্য ও মোট ব্যালেন্স", 13f, "#A9B6D5", bold = true))
        root.addView(space(7))
        if (members.isEmpty()) {
            root.addView(emptyCard("Room member যোগ করুন", "রুমে যারা থাকেন তাদের নাম আলাদা করে সেভ করুন। তারপর ভাড়া, বাজার ও বিল যোগ করুন।"))
        } else {
            members.forEachIndexed { i, member ->
                val balance = balances[member.id] ?: 0.0
                val label = when {
                    balance > 0.005 -> "পাবে ${moneyText(balance)}"
                    balance < -0.005 -> "দেবে ${moneyText(-balance)}"
                    else -> "হিসাব সমান"
                }
                val c = card("#151F37", padding = 13)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(member.name, 16f, if (member.active) "#FFFFFF" else "#8B96AF", bold = true))
                labels.addView(text(if (member.active) "● Active member" else "○ Inactive member", 10.8f, if (member.active) "#6FC9A8" else "#7180A4"))
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text(label, 14f, when { balance > 0.005 -> "#6BD5AD"; balance < -0.005 -> "#FF989E"; else -> "#9BA8C6" }, bold = true))
                c.addView(row)
                c.setOnClickListener { roomMemberActions(member) }
                root.addView(c)
                if (i < members.lastIndex) root.addView(space(7))
            }
        }

        if (expenses.isNotEmpty()) {
            root.addView(space(20))
            root.addView(text("ভাড়া / বাজার / Shared খরচ", 13f, "#A9B6D5", bold = true))
            root.addView(text("কারা PAID, কারা DUE—প্রতিটি খরচে আলাদা দেখা যাবে", 11f, "#7887AA").apply { setPadding(0, dp(3), 0, dp(7)) })
            expenses.take(15).forEachIndexed { i, item ->
                val payer = finance.memberName(item.paidById)
                val share = item.shareAmount()
                val participantIds = item.participantIds.toList()
                val paidCount = participantIds.count { finance.paymentStatus(item, it) == "PAID" }
                val partialCount = participantIds.count { finance.paymentStatus(item, it) == "PARTIAL" }
                val dueCount = participantIds.count { finance.paymentStatus(item, it) == "DUE" }
                val collection = finance.expenseCollection(item)
                val fullyPaid = collection.third < 0.005
                val c = card(if (fullyPaid) "#162B2B" else "#191F34", padding = 13)

                val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val titleLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                titleLabels.addView(text(item.title, 16f, "#FFFFFF", bold = true))
                titleLabels.addView(text(finance.kindLabel(item.kind), 10.8f, "#AAB6D1", bold = true))
                header.addView(titleLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(text(if (fullyPaid) "✓ FULL PAID" else "${paidCount}/${participantIds.size} PAID", 12f, if (fullyPaid) "#70D7B0" else "#F0C17A", bold = true))
                c.addView(header)
                c.addView(text("মোট ${moneyText(item.amount)} • দিয়েছেন $payer • ${participantIds.size} জন • জনপ্রতি ${moneyText(share)}", 11.7f, "#E4BC7E").apply { setPadding(0, dp(7), 0, 0) })
                c.addView(text("✅ Paid $paidCount  •  🟡 Partial $partialCount  •  ⏳ Due $dueCount  •  বাকি ${moneyText(collection.third)}", 11.5f, if (fullyPaid) "#70D7B0" else "#FFAAAE", bold = true).apply { setPadding(0, dp(5), 0, 0) })
                c.addView(text("${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 10.8f, "#7E8CAD").apply { setPadding(0, dp(4), 0, 0) })
                c.addView(text("Tap করুন → পেমেন্ট স্ট্যাটাস / টাকা নেওয়া / এডিট", 10.5f, "#7183B2").apply { setPadding(0, dp(5), 0, 0) })
                c.setOnClickListener { roomExpenseActions(item) }
                root.addView(c)
                if (i < minOf(14, expenses.lastIndex)) root.addView(space(8))
            }
        }

        val expensePayments = finance.roomExpensePayments()
        if (expensePayments.isNotEmpty()) {
            root.addView(space(20))
            root.addView(text("সদস্য পেমেন্ট ইতিহাস", 13f, "#A9B6D5", bold = true))
            root.addView(space(7))
            expensePayments.take(12).forEachIndexed { i, payment ->
                val expense = expenses.firstOrNull { it.id == payment.expenseId }
                val c = card("#14283A", padding = 12)
                c.addView(text("✓ ${finance.memberName(payment.memberId)} PAID ${moneyText(payment.amount)}", 14.5f, "#70D7B0", bold = true))
                c.addView(text("${expense?.title ?: "Shared খরচ"} • পেয়েছেন ${finance.memberName(payment.toMemberId)}", 11.3f, "#9EACCA").apply { setPadding(0, dp(3), 0, 0) })
                c.addView(text("${friendlyDate(payment.date)}${if (payment.note.isNotBlank()) " • ${payment.note}" else ""}", 10.5f, "#7483A6").apply { setPadding(0, dp(3), 0, 0) })
                root.addView(c)
                if (i < minOf(11, expensePayments.lastIndex)) root.addView(space(7))
            }
        }

        val settlements = finance.roomSettlements()
        if (settlements.isNotEmpty()) {
            root.addView(space(18))
            root.addView(text("অন্যান্য Settlement history", 13f, "#A9B6D5", bold = true))
            root.addView(space(7))
            settlements.take(8).forEachIndexed { i, item ->
                val c = card("#151F34", padding = 12)
                c.addView(text("${finance.memberName(item.fromMemberId)} → ${finance.memberName(item.toMemberId)}", 14f, "#FFFFFF", bold = true))
                c.addView(text("${moneyText(item.amount)} • ${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 11f, "#8C9ABA").apply { setPadding(0, dp(3), 0, 0) })
                c.setOnClickListener { roomSettlementActions(item) }
                root.addView(c)
                if (i < minOf(7, settlements.lastIndex)) root.addView(space(7))
            }
        }

'''
    new_money = money[:room_start] + room_tail + return_marker + '    }'
    ms = ms[:start] + new_money + ms[end:]

    new_add_expense = r'''    private fun addRoomExpense(existing: RoomExpense? = null) {
        val finance = FinanceStore(this)
        if (existing != null && finance.paymentsForExpense(existing.id).isNotEmpty()) {
            Toast.makeText(this, "এই খরচে payment record আছে। হিসাব নিরাপদ রাখতে payment থাকা অবস্থায় expense edit করা যাবে না।", Toast.LENGTH_LONG).show()
            return
        }

        val allMembers = finance.roomMembers()
        val visibleMembers = allMembers.filter { member ->
            member.active || existing?.participantIds?.contains(member.id) == true || existing?.paidById == member.id
        }
        val activeMembers = allMembers.filter { it.active }
        if (activeMembers.isEmpty()) {
            Toast.makeText(this, "আগে অন্তত ১ জন Active Room member যোগ করুন", Toast.LENGTH_LONG).show()
            return
        }

        val kindLabels = listOf("রুম ভাড়া", "বাজার", "বিদ্যুৎ", "ইন্টারনেট", "গ্যাস", "পানি", "অন্যান্য")
        val kindValues = listOf(
            FinanceStore.KIND_RENT, FinanceStore.KIND_MARKET, FinanceStore.KIND_ELECTRICITY,
            FinanceStore.KIND_INTERNET, FinanceStore.KIND_GAS, FinanceStore.KIND_WATER, FinanceStore.KIND_OTHER
        )
        val box = formBox()
        box.addView(text("খরচের ধরন", 12f, "#9AA8C8", bold = true))
        val kindSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kindLabels)
            val index = kindValues.indexOf(existing?.kind ?: FinanceStore.KIND_MARKET).coerceAtLeast(0)
            setSelection(index)
        }
        box.addView(kindSpinner)
        box.addView(space(8))

        val titleInput = input("নাম / বিস্তারিত (যেমন আগস্ট রুম ভাড়া)").apply { setText(existing?.title ?: "") }
        val amountInput = input("মোট টাকা").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.amount.toString())
        }
        val payerSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, visibleMembers.map { "দিয়েছেন: ${it.name}" })
            val index = visibleMembers.indexOfFirst { it.id == existing?.paidById }
            if (index >= 0) setSelection(index)
        }
        val dateInput = input("তারিখ (YYYY-MM-DD)").apply { setText(existing?.date ?: store.today()) }
        val noteInput = input("নোট (ঐচ্ছিক)").apply { setText(existing?.note ?: "") }

        box.addView(titleInput); box.addView(space(8)); box.addView(amountInput); box.addView(space(8))
        box.addView(text("কে পুরো টাকা আগে দিয়েছেন", 12f, "#9AA8C8", bold = true)); box.addView(payerSpinner); box.addView(space(10))
        val ruleText = text("", 11.5f, "#F0C17A", bold = true)
        box.addView(ruleText)
        box.addView(space(6))
        box.addView(text("কারা এই খরচের ভাগ নেবে", 12f, "#9AA8C8", bold = true))

        val memberChecks = visibleMembers.map { member ->
            member to CheckBox(this).apply {
                text = member.name + if (member.active) "" else " (Inactive)"
                setTextColor(Color.WHITE)
                isChecked = existing?.participantIds?.contains(member.id) ?: member.active
            }
        }
        memberChecks.forEach { box.addView(it.second) }
        box.addView(space(8)); box.addView(dateInput); box.addView(space(8)); box.addView(noteInput)

        fun applyKindRules() {
            val kind = kindValues[kindSpinner.selectedItemPosition]
            val rent = kind == FinanceStore.KIND_RENT
            ruleText.text = if (rent) {
                "🏠 রুম ভাড়া: সব Active member অটোমেটিক selected থাকবে এবং সমান ভাগ হবে।"
            } else if (kind == FinanceStore.KIND_MARKET) {
                "🛒 বাজার: সবাই default selected; যারা এই বাজারের ভাগ নেবে না তাদের uncheck করতে পারবেন।"
            } else {
                "এই shared খরচে প্রয়োজনমতো member select/unselect করতে পারবেন।"
            }
            memberChecks.forEach { (member, check) ->
                if (rent) {
                    check.isChecked = member.active
                    check.isEnabled = false
                } else {
                    check.isEnabled = member.active || existing?.participantIds?.contains(member.id) == true
                }
            }
            if (existing == null && titleInput.text.toString().isBlank()) titleInput.setText(kindLabels[kindSpinner.selectedItemPosition])
        }
        kindSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = applyKindRules()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        applyKindRules()

        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Room/Mess খরচ যোগ করুন" else "Shared খরচ এডিট")
            .setView(scroll)
            .setPositiveButton("সেভ") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    Toast.makeText(this, "সঠিক টাকা লিখুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val payer = visibleMembers.getOrNull(payerSpinner.selectedItemPosition)
                if (payer == null) {
                    Toast.makeText(this, "কে টাকা দিয়েছেন নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val kind = kindValues[kindSpinner.selectedItemPosition]
                val participants = if (kind == FinanceStore.KIND_RENT) {
                    activeMembers.map { it.id }.toSet()
                } else {
                    memberChecks.filter { it.second.isChecked }.map { it.first.id }.toSet()
                }
                if (participants.isEmpty()) {
                    Toast.makeText(this, "অন্তত ১ জন member ভাগে রাখুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val title = titleInput.text.toString().trim().ifBlank { kindLabels[kindSpinner.selectedItemPosition] }
                val updated = RoomExpense(
                    existing?.id ?: java.util.UUID.randomUUID().toString(),
                    title,
                    amount,
                    payer.id,
                    participants,
                    dateInput.text.toString().trim().ifBlank { store.today() },
                    noteInput.text.toString().trim(),
                    kind
                )
                val items = finance.roomExpenses()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                finance.saveRoomExpenses(items)
                CloudSyncManager.scheduleUpload(this)
                Toast.makeText(this, "${finance.kindLabel(kind)} • ${participants.size} জনে জনপ্রতি ${moneyText(updated.shareAmount())}", Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

'''
    ms = replace_function(ms, '    private fun addRoomExpense(existing: RoomExpense? = null) {', new_add_expense)

    new_expense_actions = r'''    private fun roomExpenseActions(item: RoomExpense) {
        val finance = FinanceStore(this)
        val hasDue = item.participantIds.any { finance.remainingForExpense(item, it) > 0.005 }
        val options = mutableListOf("পেমেন্ট স্ট্যাটাস দেখুন")
        if (hasDue) options += "সদস্যের পেমেন্ট নিন"
        options += "এডিট করুন"
        options += "ডিলিট করুন"
        AlertDialog.Builder(this).setTitle(item.title).setItems(options.toTypedArray()) { _, which ->
            when (options[which]) {
                "পেমেন্ট স্ট্যাটাস দেখুন" -> showRoomExpenseStatus(item)
                "সদস্যের পেমেন্ট নিন" -> recordRoomExpensePayment(item)
                "এডিট করুন" -> addRoomExpense(item)
                "ডিলিট করুন" -> {
                    val items = finance.roomExpenses()
                    items.removeAll { it.id == item.id }
                    finance.saveRoomExpenses(items)
                    finance.deleteExpensePayments(item.id)
                    CloudSyncManager.scheduleUpload(this)
                    render()
                }
            }
        }.show()
    }

'''
    ms = replace_function(ms, '    private fun roomExpenseActions(item: RoomExpense) {', new_expense_actions)

    helper_anchor = '    private fun addRoomSettlement(existing: RoomSettlement? = null) {'
    require(ms, helper_anchor, 'addRoomSettlement anchor')
    helpers = r'''    private fun showRoomExpenseStatus(item: RoomExpense) {
        val finance = FinanceStore(this)
        val box = formBox()
        val payer = finance.memberName(item.paidById)
        val share = item.shareAmount()
        val collection = finance.expenseCollection(item)
        box.addView(text("${finance.kindLabel(item.kind)} • ${item.title}", 17f, "#FFFFFF", bold = true))
        box.addView(text("মোট ${moneyText(item.amount)} • জনপ্রতি ${moneyText(share)}", 13f, "#E4BC7E", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        box.addView(text("পুরো টাকা আগে দিয়েছেন: $payer", 12f, "#9DABCA").apply { setPadding(0, dp(4), 0, 0) })
        box.addView(text("পেমেন্ট এসেছে ${moneyText(collection.second)} • বাকি ${moneyText(collection.third)}", 12.5f, if (collection.third < 0.005) "#70D7B0" else "#FFAAAE", bold = true).apply { setPadding(0, dp(4), 0, dp(10)) })

        val memberMap = finance.roomMembers().associateBy { it.id }
        item.participantIds.mapNotNull { memberMap[it] }.forEach { member ->
            val status = finance.paymentStatus(item, member.id)
            val paid = finance.paidForExpense(item, member.id)
            val due = finance.remainingForExpense(item, member.id)
            val statusLabel = when (status) {
                "PAID" -> "✓ PAID"
                "PARTIAL" -> "◐ PARTIAL"
                else -> "⏳ DUE"
            }
            val statusColor = when (status) {
                "PAID" -> "#70D7B0"
                "PARTIAL" -> "#F0C17A"
                else -> "#FF9DA3"
            }
            val c = card("#151F37", padding = 11)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(member.name, 14.5f, "#FFFFFF", bold = true))
            labels.addView(text(if (member.id == item.paidById) "মূল payer • নিজের share পরিশোধিত" else "Paid ${moneyText(paid)} • Due ${moneyText(due)}", 10.8f, "#8795B8"))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text(statusLabel, 12f, statusColor, bold = true))
            c.addView(row)
            box.addView(c)
            box.addView(space(6))
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Payment status")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("বন্ধ", null)
        if (collection.third > 0.005) builder.setPositiveButton("পেমেন্ট নিন") { _, _ -> recordRoomExpensePayment(item) }
        builder.show()
    }

    private fun recordAnyRoomExpensePayment() {
        val finance = FinanceStore(this)
        val pending = finance.roomExpenses().filter { item -> item.participantIds.any { finance.remainingForExpense(item, it) > 0.005 } }
        if (pending.isEmpty()) {
            Toast.makeText(this, "সব shared খরচের member payment সম্পন্ন", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = pending.map { item -> "${item.title} • বাকি ${moneyText(finance.expenseCollection(item).third)}" }
        AlertDialog.Builder(this).setTitle("কোন খরচের পেমেন্ট?").setItems(labels.toTypedArray()) { _, which ->
            recordRoomExpensePayment(pending[which])
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun recordRoomExpensePayment(item: RoomExpense) {
        val finance = FinanceStore(this)
        val memberMap = finance.roomMembers().associateBy { it.id }
        val candidates = item.participantIds.mapNotNull { memberMap[it] }.filter {
            it.id != item.paidById && finance.remainingForExpense(item, it.id) > 0.005
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "এই খরচের সবাই PAID", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = candidates.map { member -> "${member.name} • Due ${moneyText(finance.remainingForExpense(item, member.id))}" }
        AlertDialog.Builder(this).setTitle("কে টাকা দিয়েছে?").setItems(labels.toTypedArray()) { _, which ->
            recordRoomExpensePaymentForMember(item, candidates[which])
        }.setNegativeButton("বাতিল", null).show()
    }

    private fun recordRoomExpensePaymentForMember(item: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val remaining = finance.remainingForExpense(item, member.id)
        if (remaining < 0.005) {
            Toast.makeText(this, "${member.name} ইতিমধ্যে PAID", Toast.LENGTH_SHORT).show()
            return
        }
        val box = formBox()
        box.addView(text("${member.name} → ${finance.memberName(item.paidById)}", 15f, "#FFFFFF", bold = true))
        box.addView(text("${item.title} • বাকি ${moneyText(remaining)}", 11.5f, "#F0C17A").apply { setPadding(0, dp(4), 0, dp(8)) })
        val amount = input("কত টাকা দিয়েছে").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", remaining))
        }
        val date = input("তারিখ (YYYY-MM-DD)").apply { setText(store.today()) }
        val note = input("নোট (ঐচ্ছিক)")
        box.addView(amount); box.addView(space(8)); box.addView(date); box.addView(space(8)); box.addView(note)
        AlertDialog.Builder(this).setTitle("Member payment")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("PAID সেভ") { _, _ ->
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
                val left = (remaining - value).coerceAtLeast(0.0)
                Toast.makeText(this, if (left < 0.005) "✓ ${member.name} এখন PAID" else "${member.name} PARTIAL • বাকি ${moneyText(left)}", Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

'''
    ms = ms.replace(helper_anchor, helpers + helper_anchor, 1)

    mp.write_text(ms)
    print('v3.22 Room/Mess rent split, per-expense PAID/PARTIAL/DUE and payment UI applied')
else:
    print('v3.22 MainActivity patch already applied')

# Version metadata after v3.21.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
if 'versionName = "3.21.0"' in bs:
    bs = bs.replace('versionCode = 34', 'versionCode = 35', 1)
    bs = bs.replace('versionName = "3.21.0"', 'versionName = "3.22.0"', 1)
    bp.write_text(bs)
    print('v3.22 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
updated = re.sub(r'("appVersion"\s+to\s+")[0-9.]+("\s*\))', r'\g<1>3.22.0\2', cs, count=1)
if updated != cs:
    cp.write_text(updated)
    print('v3.22 cloud metadata applied')
