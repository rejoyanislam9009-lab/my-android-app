from pathlib import Path


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


def replace_kotlin_function(text: str, signature: str, replacement: str) -> tuple[str, bool]:
    start = text.find(signature)
    if start < 0:
        return text, False
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        ch = text[i]
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'closing brace not found: {signature}')
    while end < len(text) and text[end] == '\n':
        end += 1
    return text[:start] + replacement + text[end:], True


# ---------------------------------------------------------------------------
# Guide v3.21 Finance Center
# - Daily personal expense/income dashboard.
# - Named receivable/payable ledger with partial payments.
# - Room/Mess members, shared expenses, equal split across selected members,
#   member balances, and settlement/payment history.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideFinanceCenterV321' not in ms:
    money_page = r'''    // GuideFinanceCenterV321
    private fun moneyPage(): LinearLayout {
        val root = page()
        detailHeader(root, "হিসাব কেন্দ্র", "দৈনিক খরচ, পাওনা-দেনা এবং Room/Mess হিসাব এক জায়গায়।", "+ যোগ") { addMoneyRecord() }

        val finance = FinanceStore(this)
        val records = store.moneyRecords()
        val todayExpense = records.filter { it.date == store.today() && it.type == "Expense" }.sumOf { it.amount }
        val todayIncome = records.filter { it.date == store.today() && it.type == "Income" }.sumOf { it.amount }
        val month = store.currentMonthMoneySummary()
        val debtSummary = finance.debtSummary()

        val hero = card("#17263F")
        hero.addView(text("আজকের হিসাব", 12f, "#9BA9CC", bold = true))
        hero.addView(text("খরচ ${moneyText(todayExpense)}", 25f, "#FF9A9F", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        hero.addView(text("আজ আয় ${moneyText(todayIncome)}  •  মাসে খরচ ${moneyText(month.second)}", 13f, "#AFBAD6").apply { setPadding(0, dp(6), 0, 0) })
        hero.addView(text("মাসিক ব্যালেন্স ${moneyText(month.third)}", 14f, if (month.third >= 0) "#6ED8B0" else "#FF8D94", bold = true).apply { setPadding(0, dp(6), 0, 0) })
        root.addView(hero)
        root.addView(space(12))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(smallAction("খরচ / আয়", "#5A50D3") { addMoneyRecord() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        quick.addView(hSpace(8))
        quick.addView(smallAction("পাওনা / দেনা", "#2E7C70") { addDebtRecord() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(quick)
        root.addView(space(8))
        val roomQuick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        roomQuick.addView(smallAction("Room member", "#425B86") { addRoomMember() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        roomQuick.addView(hSpace(8))
        roomQuick.addView(smallAction("Room খরচ", "#8A5C36") { addRoomExpense() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        roomQuick.addView(hSpace(8))
        roomQuick.addView(smallAction("টাকা দেওয়া", "#6B4B86") { addRoomSettlement() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(roomQuick)

        root.addView(space(20))
        root.addView(sectionTitle("ব্যক্তিগত দৈনিক খরচ / আয়"))
        if (records.isEmpty()) {
            root.addView(emptyCard("এখনও কোনো হিসাব নেই", "প্রতিদিন কী খরচ বা আয় হলো এখানে লিখে রাখুন।"))
        } else {
            records.take(12).forEachIndexed { i, item ->
                val expense = item.type == "Expense"
                val sign = if (expense) "−" else "+"
                val c = card("#151F3A")
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(item.category, 16f, "#FFFFFF", bold = true))
                labels.addView(text("${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 12f, "#8795B9"))
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text("$sign${moneyText(item.amount)}", 16f, if (expense) "#FF8E95" else "#64D2AA", bold = true))
                c.addView(row)
                c.setOnClickListener { moneyActions(item) }
                root.addView(c)
                if (i < minOf(11, records.lastIndex)) root.addView(space(8))
            }
        }

        root.addView(space(22))
        root.addView(sectionTitle("পাওনা / দেনা"))
        val debtHero = card("#16263C")
        debtHero.addView(text("আমি পাব ${moneyText(debtSummary.first)}", 17f, "#6ED8B0", bold = true))
        debtHero.addView(text("আমি দেব ${moneyText(debtSummary.second)}", 17f, "#FF9B9F", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        debtHero.addView(text("নাম অনুযায়ী হিসাব সেভ থাকে • আংশিক টাকা দেওয়া/পাওয়া রেকর্ড করা যায়", 11.5f, "#91A0C4").apply { setPadding(0, dp(7), 0, 0) })
        root.addView(debtHero)
        root.addView(space(9))
        root.addView(pillButton("+ নতুন পাওনা / দেনা যোগ করুন", "#2E756C") { addDebtRecord() })
        root.addView(space(10))
        val debts = finance.debts()
        if (debts.isEmpty()) {
            root.addView(emptyCard("কোনো পাওনা/দেনা নেই", "যার কাছে টাকা পাবেন বা যাকে টাকা দেবেন তার নাম দিয়ে সেভ করুন।"))
        } else {
            debts.take(20).forEachIndexed { i, item ->
                val receive = item.direction == FinanceStore.RECEIVE
                val remaining = item.remaining()
                val status = if (item.isSettled()) "মিটে গেছে" else if (receive) "আপনি পাবেন" else "আপনি দেবেন"
                val c = card(if (item.isSettled()) "#1A2637" else "#17243D")
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(item.person, 16f, "#FFFFFF", bold = true))
                labels.addView(text("$status • বাকি ${moneyText(remaining)}", 12.5f, if (item.isSettled()) "#8290AA" else if (receive) "#73D7B2" else "#FF9EA3", bold = true))
                labels.addView(text("মোট ${moneyText(item.amount)} • দেওয়া/পাওয়া ${moneyText(item.paidAmount)} • ${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 11f, "#8391B6").apply { setPadding(0, dp(3), 0, 0) })
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text("›", 26f, "#7180A5"))
                c.addView(row)
                c.setOnClickListener { debtActions(item) }
                root.addView(c)
                if (i < minOf(19, debts.lastIndex)) root.addView(space(8))
            }
        }

        root.addView(space(22))
        root.addView(sectionTitle("Room / Mess হিসাব"))
        val members = finance.roomMembers()
        val activeMembers = members.filter { it.active }
        val balances = finance.roomBalances()
        val roomHero = card("#1B263B")
        roomHero.addView(text("এই মাসের Room/Mess খরচ", 12f, "#98A5C6", bold = true))
        roomHero.addView(text(moneyText(finance.currentMonthRoomExpense()), 24f, "#F0C17A", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        roomHero.addView(text("Active member ${activeMembers.size} • খরচ সমান ভাগে নির্বাচিত সদস্যদের মধ্যে ভাগ হয়", 11.5f, "#8E9CBE").apply { setPadding(0, dp(6), 0, 0) })
        root.addView(roomHero)
        root.addView(space(9))

        val roomActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        roomActions.addView(smallAction("+ সদস্য", "#425B86") { addRoomMember() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        roomActions.addView(hSpace(7))
        roomActions.addView(smallAction("+ খরচ", "#8A5C36") { addRoomExpense() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        roomActions.addView(hSpace(7))
        roomActions.addView(smallAction("+ Settlement", "#6B4B86") { addRoomSettlement() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(roomActions)
        root.addView(space(12))

        if (members.isEmpty()) {
            root.addView(emptyCard("Room member যোগ করুন", "রুমে যারা থাকেন তাদের নাম আগে যোগ করুন। তারপর বাজার/ভাড়া/বিদ্যুৎ/ইন্টারনেটসহ shared খরচ লিখুন।"))
        } else {
            root.addView(text("কে কত পাবে / দেবে", 13f, "#A9B6D5", bold = true))
            root.addView(space(7))
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
                labels.addView(text(member.name, 15.5f, if (member.active) "#FFFFFF" else "#8B96AF", bold = true))
                labels.addView(text(if (member.active) "Active member" else "Inactive member", 10.5f, "#7180A4"))
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text(label, 14f, when { balance > 0.005 -> "#6BD5AD"; balance < -0.005 -> "#FF989E"; else -> "#9BA8C6" }, bold = true))
                c.addView(row)
                c.setOnClickListener { roomMemberActions(member) }
                root.addView(c)
                if (i < members.lastIndex) root.addView(space(7))
            }
        }

        val expenses = finance.roomExpenses()
        if (expenses.isNotEmpty()) {
            root.addView(space(18))
            root.addView(text("সাম্প্রতিক shared খরচ", 13f, "#A9B6D5", bold = true))
            root.addView(space(7))
            expenses.take(10).forEachIndexed { i, item ->
                val payer = finance.memberName(item.paidById)
                val share = if (item.participantIds.isEmpty()) 0.0 else item.amount / item.participantIds.size
                val c = card("#191F34", padding = 13)
                c.addView(text(item.title, 15.5f, "#FFFFFF", bold = true))
                c.addView(text("${moneyText(item.amount)} • দিয়েছেন $payer • ${item.participantIds.size} জন • জনপ্রতি ${moneyText(share)}", 11.5f, "#E4BC7E").apply { setPadding(0, dp(4), 0, 0) })
                c.addView(text("${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 10.8f, "#7E8CAD").apply { setPadding(0, dp(3), 0, 0) })
                c.setOnClickListener { roomExpenseActions(item) }
                root.addView(c)
                if (i < minOf(9, expenses.lastIndex)) root.addView(space(7))
            }
        }

        val settlements = finance.roomSettlements()
        if (settlements.isNotEmpty()) {
            root.addView(space(18))
            root.addView(text("কে কাকে টাকা দিয়েছে", 13f, "#A9B6D5", bold = true))
            root.addView(space(7))
            settlements.take(10).forEachIndexed { i, item ->
                val from = finance.memberName(item.fromMemberId)
                val to = finance.memberName(item.toMemberId)
                val c = card("#191D31", padding = 12)
                c.addView(text("$from → $to", 14.5f, "#FFFFFF", bold = true))
                c.addView(text("${moneyText(item.amount)} • ${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 11.5f, "#B4A2D5").apply { setPadding(0, dp(4), 0, 0) })
                c.setOnClickListener { roomSettlementActions(item) }
                root.addView(c)
                if (i < minOf(9, settlements.lastIndex)) root.addView(space(7))
            }
        }
        return root
    }

'''
    ms, ok = replace_kotlin_function(ms, '    private fun moneyPage(): LinearLayout {', money_page)
    if not ok:
        raise SystemExit('pattern not found: moneyPage')

    add_money = r'''    private fun addMoneyRecord(existing: MoneyRecord? = null) {
        val box = formBox()
        val typeSpinner = Spinner(this)
        val types = listOf("Expense", "Income")
        val labels = listOf("খরচ", "আয়")
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        if (existing?.type == "Income") typeSpinner.setSelection(1)
        val amountInput = input("টাকার পরিমাণ").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.amount.toString())
        }
        val categoryInput = input("খরচ/আয়ের নাম বা ক্যাটাগরি").apply { setText(existing?.category ?: "") }
        val noteInput = input("নোট (ঐচ্ছিক)").apply { setText(existing?.note ?: "") }
        val dateInput = input("তারিখ YYYY-MM-DD").apply { setText(existing?.date ?: store.today()) }
        box.addView(typeSpinner); box.addView(space(8)); box.addView(amountInput); box.addView(space(8)); box.addView(categoryInput); box.addView(space(8)); box.addView(noteInput); box.addView(space(8)); box.addView(dateInput)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "খরচ / আয় যোগ করুন" else "হিসাব এডিট করুন")
            .setView(scrollableDialogContent(box))
            .setPositiveButton("সেভ") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                val date = dateInput.text.toString().trim().ifBlank { store.today() }
                if (amount <= 0) {
                    Toast.makeText(this, "সঠিক টাকার পরিমাণ দিন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (runCatching { LocalDate.parse(date) }.isFailure) {
                    Toast.makeText(this, "তারিখ YYYY-MM-DD লিখুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = MoneyRecord(
                    existing?.id ?: java.util.UUID.randomUUID().toString(),
                    types[typeSpinner.selectedItemPosition],
                    amount,
                    categoryInput.text.toString().trim().ifBlank { if (typeSpinner.selectedItemPosition == 0) "দৈনিক খরচ" else "আয়" },
                    noteInput.text.toString().trim(),
                    date
                )
                val items = store.moneyRecords()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                store.saveMoneyRecords(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

'''
    ms, ok = replace_kotlin_function(ms, '    private fun addMoneyRecord(existing: MoneyRecord? = null) {', add_money)
    if not ok:
        raise SystemExit('pattern not found: addMoneyRecord')

    money_actions = r'''    private fun moneyActions(item: MoneyRecord) {
        AlertDialog.Builder(this).setTitle(item.category).setItems(arrayOf("এডিট করুন", "ডিলিট করুন")) { _, which ->
            if (which == 0) {
                addMoneyRecord(item)
            } else {
                val items = store.moneyRecords()
                items.removeAll { it.id == item.id }
                store.saveMoneyRecords(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
        }.show()
    }

'''
    ms, ok = replace_kotlin_function(ms, '    private fun moneyActions(item: MoneyRecord) {', money_actions)
    if not ok:
        raise SystemExit('pattern not found: moneyActions')

    anchor = '    private fun attendancePage(): LinearLayout {\n'
    require(ms, anchor, 'attendance page anchor')
    helpers = r'''    private fun addDebtRecord(existing: DebtRecord? = null) {
        val finance = FinanceStore(this)
        val box = formBox()
        val person = input("নাম").apply { setText(existing?.person ?: "") }
        val direction = Spinner(this)
        val directions = listOf("আমি পাব", "আমি দেব")
        direction.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, directions)
        if (existing?.direction == FinanceStore.PAY) direction.setSelection(1)
        val amount = input("মোট টাকার পরিমাণ").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.amount.toString())
        }
        val note = input("কেন / নোট (ঐচ্ছিক)").apply { setText(existing?.note ?: "") }
        val date = input("তারিখ YYYY-MM-DD").apply { setText(existing?.date ?: store.today()) }
        box.addView(person); box.addView(space(8)); box.addView(direction); box.addView(space(8)); box.addView(amount); box.addView(space(8)); box.addView(note); box.addView(space(8)); box.addView(date)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "নতুন পাওনা / দেনা" else "পাওনা / দেনা এডিট")
            .setView(scrollableDialogContent(box))
            .setPositiveButton("সেভ") { _, _ ->
                val name = person.text.toString().trim()
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                val day = date.text.toString().trim().ifBlank { store.today() }
                if (name.isBlank() || value <= 0.0 || runCatching { LocalDate.parse(day) }.isFailure) {
                    Toast.makeText(this, "নাম, সঠিক টাকা এবং YYYY-MM-DD তারিখ দিন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = DebtRecord(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    person = name,
                    direction = if (direction.selectedItemPosition == 0) FinanceStore.RECEIVE else FinanceStore.PAY,
                    amount = value,
                    paidAmount = (existing?.paidAmount ?: 0.0).coerceAtMost(value),
                    note = note.text.toString().trim(),
                    date = day
                )
                val items = finance.debts()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                finance.saveDebts(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun debtActions(item: DebtRecord) {
        val settledLabel = if (item.isSettled()) "মিটে গেছে" else "সম্পূর্ণ মিটেছে"
        AlertDialog.Builder(this).setTitle(item.person).setItems(arrayOf("টাকা দেওয়া/পাওয়া যোগ করুন", "এডিট করুন", settledLabel, "ডিলিট করুন")) { _, which ->
            val finance = FinanceStore(this)
            val items = finance.debts()
            val index = items.indexOfFirst { it.id == item.id }
            if (index < 0) return@setItems
            when (which) {
                0 -> { recordDebtPayment(item); return@setItems }
                1 -> { addDebtRecord(item); return@setItems }
                2 -> items[index] = item.copy(paidAmount = item.amount)
                3 -> items.removeAt(index)
            }
            finance.saveDebts(items)
            CloudSyncManager.scheduleUpload(this)
            render()
        }.show()
    }

    private fun recordDebtPayment(item: DebtRecord) {
        val remaining = item.remaining()
        if (remaining <= 0.005) {
            Toast.makeText(this, "এই হিসাব ইতিমধ্যে মিটে গেছে", Toast.LENGTH_SHORT).show()
            return
        }
        val input = input("কত টাকা ${if (item.direction == FinanceStore.RECEIVE) "পেয়েছেন" else "দিয়েছেন"}").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle("বাকি ${moneyText(remaining)}")
            .setView(input)
            .setPositiveButton("সেভ") { _, _ ->
                val value = input.text.toString().toDoubleOrNull() ?: 0.0
                if (value <= 0.0) return@setPositiveButton
                val finance = FinanceStore(this)
                val items = finance.debts()
                val index = items.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    items[index] = item.copy(paidAmount = (item.paidAmount + value).coerceAtMost(item.amount))
                    finance.saveDebts(items)
                    CloudSyncManager.scheduleUpload(this)
                    render()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun addRoomMember(existing: RoomMember? = null) {
        val finance = FinanceStore(this)
        val box = formBox()
        val name = input("সদস্যের নাম").apply { setText(existing?.name ?: "") }
        val active = CheckBox(this).apply {
            text = "এই সদস্য বর্তমানে Room/Mess-এ আছে"
            setTextColor(Color.WHITE)
            isChecked = existing?.active ?: true
        }
        box.addView(name); box.addView(space(8)); box.addView(active)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Room member যোগ করুন" else "Member এডিট করুন")
            .setView(box)
            .setPositiveButton("সেভ") { _, _ ->
                val value = name.text.toString().trim()
                if (value.isBlank()) return@setPositiveButton
                val updated = RoomMember(existing?.id ?: java.util.UUID.randomUUID().toString(), value, active.isChecked)
                val items = finance.roomMembers()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                finance.saveRoomMembers(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun roomMemberActions(item: RoomMember) {
        AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf("নাম/স্ট্যাটাস এডিট করুন", if (item.active) "Inactive করুন" else "Active করুন")) { _, which ->
            if (which == 0) {
                addRoomMember(item)
            } else {
                val finance = FinanceStore(this)
                val items = finance.roomMembers()
                val index = items.indexOfFirst { it.id == item.id }
                if (index >= 0) items[index] = item.copy(active = !item.active)
                finance.saveRoomMembers(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
        }.show()
    }

    private fun addRoomExpense(existing: RoomExpense? = null) {
        val finance = FinanceStore(this)
        val allMembers = finance.roomMembers()
        val visibleMembers = allMembers.filter { member ->
            member.active || existing?.participantIds?.contains(member.id) == true || existing?.paidById == member.id
        }
        if (visibleMembers.isEmpty()) {
            Toast.makeText(this, "আগে অন্তত একজন Room member যোগ করুন", Toast.LENGTH_LONG).show()
            addRoomMember()
            return
        }

        val box = formBox()
        val title = input("খরচের নাম: বাজার / ভাড়া / বিদ্যুৎ...").apply { setText(existing?.title ?: "") }
        val amount = input("মোট টাকা").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.amount.toString())
        }
        val payer = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, visibleMembers.map { it.name })
            val selected = visibleMembers.indexOfFirst { it.id == existing?.paidById }
            if (selected >= 0) setSelection(selected)
        }
        val date = input("তারিখ YYYY-MM-DD").apply { setText(existing?.date ?: store.today()) }
        val note = input("নোট (ঐচ্ছিক)").apply { setText(existing?.note ?: "") }

        box.addView(title); box.addView(space(8)); box.addView(amount); box.addView(space(8))
        box.addView(text("কে টাকা দিয়েছে", 12f, "#AAB6D2", bold = true)); box.addView(payer); box.addView(space(10))
        box.addView(text("কার কার মধ্যে এই খরচ ভাগ হবে", 12f, "#AAB6D2", bold = true))
        val checks = visibleMembers.map { member ->
            CheckBox(this).apply {
                text = member.name
                setTextColor(Color.WHITE)
                isChecked = existing?.participantIds?.contains(member.id) ?: member.active
                box.addView(this)
            } to member
        }
        box.addView(space(8)); box.addView(date); box.addView(space(8)); box.addView(note)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Room/Mess shared খরচ" else "Shared খরচ এডিট")
            .setView(scrollableDialogContent(box))
            .setPositiveButton("সেভ") { _, _ ->
                val name = title.text.toString().trim()
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                val day = date.text.toString().trim().ifBlank { store.today() }
                val participants = checks.filter { it.first.isChecked }.map { it.second.id }.toSet()
                if (name.isBlank() || value <= 0.0 || participants.isEmpty() || runCatching { LocalDate.parse(day) }.isFailure) {
                    Toast.makeText(this, "খরচের নাম, টাকা, অন্তত একজন সদস্য এবং সঠিক তারিখ দিন", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val updated = RoomExpense(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    title = name,
                    amount = value,
                    paidById = visibleMembers[payer.selectedItemPosition].id,
                    participantIds = participants,
                    date = day,
                    note = note.text.toString().trim()
                )
                val items = finance.roomExpenses()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                finance.saveRoomExpenses(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun roomExpenseActions(item: RoomExpense) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf("এডিট করুন", "ডিলিট করুন")) { _, which ->
            if (which == 0) {
                addRoomExpense(item)
            } else {
                val finance = FinanceStore(this)
                val items = finance.roomExpenses()
                items.removeAll { it.id == item.id }
                finance.saveRoomExpenses(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
        }.show()
    }

    private fun addRoomSettlement(existing: RoomSettlement? = null) {
        val finance = FinanceStore(this)
        val allMembers = finance.roomMembers()
        val visibleMembers = allMembers.filter { it.active || it.id == existing?.fromMemberId || it.id == existing?.toMemberId }
        if (visibleMembers.size < 2) {
            Toast.makeText(this, "Settlement করতে অন্তত ২ জন Room member লাগবে", Toast.LENGTH_LONG).show()
            return
        }
        val box = formBox()
        val from = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, visibleMembers.map { it.name })
            val selected = visibleMembers.indexOfFirst { it.id == existing?.fromMemberId }
            if (selected >= 0) setSelection(selected)
        }
        val to = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, visibleMembers.map { it.name })
            val selected = visibleMembers.indexOfFirst { it.id == existing?.toMemberId }
            if (selected >= 0) setSelection(selected) else if (visibleMembers.size > 1) setSelection(1)
        }
        val amount = input("কত টাকা দিয়েছে").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.amount.toString())
        }
        val date = input("তারিখ YYYY-MM-DD").apply { setText(existing?.date ?: store.today()) }
        val note = input("নোট (ঐচ্ছিক)").apply { setText(existing?.note ?: "") }
        box.addView(text("কে টাকা দিয়েছে", 12f, "#AAB6D2", bold = true)); box.addView(from); box.addView(space(8))
        box.addView(text("কাকে দিয়েছে", 12f, "#AAB6D2", bold = true)); box.addView(to); box.addView(space(8))
        box.addView(amount); box.addView(space(8)); box.addView(date); box.addView(space(8)); box.addView(note)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Room টাকা দেওয়া / Settlement" else "Settlement এডিট")
            .setView(scrollableDialogContent(box))
            .setPositiveButton("সেভ") { _, _ ->
                val fromMember = visibleMembers[from.selectedItemPosition]
                val toMember = visibleMembers[to.selectedItemPosition]
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                val day = date.text.toString().trim().ifBlank { store.today() }
                if (fromMember.id == toMember.id || value <= 0.0 || runCatching { LocalDate.parse(day) }.isFailure) {
                    Toast.makeText(this, "দুইজন আলাদা সদস্য, সঠিক টাকা এবং তারিখ দিন", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val updated = RoomSettlement(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    fromMemberId = fromMember.id,
                    toMemberId = toMember.id,
                    amount = value,
                    date = day,
                    note = note.text.toString().trim()
                )
                val items = finance.roomSettlements()
                val index = items.indexOfFirst { it.id == updated.id }
                if (index >= 0) items[index] = updated else items.add(updated)
                finance.saveRoomSettlements(items)
                CloudSyncManager.scheduleUpload(this)
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun roomSettlementActions(item: RoomSettlement) {
        val finance = FinanceStore(this)
        AlertDialog.Builder(this)
            .setTitle("${finance.memberName(item.fromMemberId)} → ${finance.memberName(item.toMemberId)}")
            .setItems(arrayOf("এডিট করুন", "ডিলিট করুন")) { _, which ->
                if (which == 0) {
                    addRoomSettlement(item)
                } else {
                    val items = finance.roomSettlements()
                    items.removeAll { it.id == item.id }
                    finance.saveRoomSettlements(items)
                    CloudSyncManager.scheduleUpload(this)
                    render()
                }
            }
            .show()
    }

'''
    ms = ms.replace(anchor, helpers + anchor, 1)
    mp.write_text(ms)
    print('v3.21 Finance Center UI, debt ledger and Room/Mess auto-split applied')
else:
    print('v3.21 MainActivity finance patch already applied')

# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
require(bs, 'versionCode = 33', 'v3.20 versionCode')
require(bs, 'versionName = "3.20.0"', 'v3.20 versionName')
bs = bs.replace('versionCode = 33', 'versionCode = 34', 1)
bs = bs.replace('versionName = "3.20.0"', 'versionName = "3.21.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
cs = cs.replace('"appVersion" to "3.20.0"', '"appVersion" to "3.21.0"', 1)
cp.write_text(cs)
print('v3.21 version metadata applied')
