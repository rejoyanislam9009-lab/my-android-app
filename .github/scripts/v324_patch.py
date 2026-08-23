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
        if text[i] == '{': depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'closing brace not found: {signature}')
    while end < len(text) and text[end] == '\n': end += 1
    return start, end


def replace_function(text: str, signature: str, replacement: str) -> str:
    bounds = function_bounds(text, signature)
    if not bounds:
        raise SystemExit(f'function not found: {signature}')
    start, end = bounds
    return text[:start] + replacement + text[end:]


mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideRoomMessSimpleV324' not in ms:
    bounds = function_bounds(ms, '    private fun moneyPage(): LinearLayout {')
    if not bounds:
        raise SystemExit('moneyPage not found')
    start, end = bounds
    money = ms[start:end]
    marker = '        // GuideRoomMessSeparatedV323\n'
    require(money, marker, 'v3.23 Room/Mess marker')
    room_start = money.index(marker)
    return_marker = '        return root\n'
    return_pos = money.rfind(return_marker)
    if return_pos < room_start:
        raise SystemExit('moneyPage return marker not found')

    room_tail = r'''        // GuideRoomMessSimpleV324
        val financeRoom = finance
        val allRoomMembers = financeRoom.roomMembers()
        val members = allRoomMembers.filter { !it.deleted }
        val activeMembers = members.filter { it.active }

        // v3.24 fixes the confusing old "upfront payer" behavior. If an older
        // v3.23 expense has no real member-payment records yet, nobody is
        // treated as paid automatically. Existing expenses that already have
        // payment records are left untouched for history safety.
        if (!uiPrefs.getBoolean("room_no_fake_payer_migrated_v324", false)) {
            val migration = financeRoom.roomExpenses()
            var changed = false
            migration.indices.forEach { i ->
                val item = migration[i]
                if (item.paidById.isNotBlank() && financeRoom.paymentsForExpense(item.id).isEmpty()) {
                    migration[i] = item.copy(paidById = "")
                    changed = true
                }
            }
            if (changed) {
                financeRoom.saveRoomExpenses(migration)
                CloudSyncManager.scheduleUpload(this)
            }
            uiPrefs.edit().putBoolean("room_no_fake_payer_migrated_v324", true).apply()
        }

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

        fun total(items: List<RoomExpense>) = items.sumOf { it.amount }
        fun paid(items: List<RoomExpense>) = items.sumOf { financeRoom.expenseCollection(it).second }
        fun due(items: List<RoomExpense>) = items.sumOf { financeRoom.expenseCollection(it).third }

        root.addView(space(22))
        root.addView(sectionTitle("Room / Mess হিসাব"))
        root.addView(text("সহজ নিয়ম: হিসাব সেভ করলে শুরুতে কেউ Paid নয়। Member-এর টাকা আসলে তখন Payment Complete করুন।", 11.5f, "#8492B6").apply { setPadding(0, dp(3), 0, dp(10)) })

        val rentTotal = total(monthRent)
        val rentPaid = paid(monthRent)
        val rentDue = due(monthRent)
        val rentHero = card(if (rentTotal > 0.005 && rentDue < 0.005) "#16302B" else "#1B263B")
        rentHero.addView(text("🏠 এই মাসের Room Rent", 13f, "#AEBBD5", bold = true))
        rentHero.addView(text("মোট ${moneyText(rentTotal)}", 25f, "#F0C17A", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        rentHero.addView(text("✓ পেমেন্ট ${moneyText(rentPaid)}", 14f, "#6FD5AE", bold = true).apply { setPadding(0, dp(6), 0, 0) })
        rentHero.addView(text("⏳ Pending ${moneyText(rentDue)}", 14f, if (rentDue < 0.005 && rentTotal > 0.005) "#6FD5AE" else "#FF9EA4", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        rentHero.addView(text(when {
            rentTotal < 0.005 -> "এই মাসে এখনও Room Rent যোগ করা হয়নি"
            rentDue < 0.005 -> "✓ SUCCESSFUL • এই মাসের Room Rent সম্পূর্ণ পরিশোধ হয়েছে"
            else -> "${activeMembers.size} Active member • যার payment হবে তার card থেকেই Complete করুন"
        }, 11.3f, if (rentTotal > 0.005 && rentDue < 0.005) "#70D7B0" else "#8F9DBD", bold = rentTotal > 0.005).apply { setPadding(0, dp(7), 0, 0) })
        root.addView(rentHero)
        root.addView(space(9))

        val messTotal = total(monthMess)
        val messPaid = paid(monthMess)
        val messDue = due(monthMess)
        val messHero = card(if (messTotal > 0.005 && messDue < 0.005) "#16302B" else "#172A37")
        messHero.addView(text("🛒 এই মাসের Mess / বাজার", 13f, "#AEBBD5", bold = true))
        messHero.addView(text("মোট ${moneyText(messTotal)}  •  Paid ${moneyText(messPaid)}  •  Pending ${moneyText(messDue)}", 14.5f, if (messTotal > 0.005 && messDue < 0.005) "#70D7B0" else "#73D2B2", bold = true).apply { setPadding(0, dp(5), 0, 0) })
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
        actionRow2.addView(smallAction("📄 PDF / WhatsApp", "#28765E") { shareRoomMessPdf() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actionRow2)

        root.addView(space(20))
        root.addView(text("সদস্য ও Payment", 15f, "#B4C0DD", bold = true))
        root.addView(text("Member card ট্যাপ করুন → এই মাসের বাকি payment এক ক্লিকে Complete করা যাবে।", 10.8f, "#7787AA").apply { setPadding(0, dp(3), 0, dp(8)) })
        if (members.isEmpty()) {
            root.addView(emptyCard("কোনো Room/Mess member নেই", "নাম দিয়ে সদস্য যোগ করুন।"))
        } else {
            val monthExpenseIds = monthExpenses.map { it.id }.toSet()
            members.forEachIndexed { index, member ->
                val memberMonthExpenses = monthExpenses.filter { member.id in it.participantIds }
                val ownShare = memberMonthExpenses.sumOf { it.shareAmount() }
                val ownDue = memberMonthExpenses.sumOf { financeRoom.remainingForExpense(it, member.id) }
                val paidNow = allPayments.filter { it.memberId == member.id && it.expenseId in monthExpenseIds }.sumOf { it.amount }
                val status = when {
                    ownShare < 0.005 -> "এই মাসে share নেই"
                    ownDue < 0.005 -> "✓ PAYMENT COMPLETE"
                    else -> "⏳ বাকি ${moneyText(ownDue)}"
                }
                val c = card(if (ownShare > 0.005 && ownDue < 0.005) "#16302B" else "#151F37", padding = 13)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(member.name, 16f, if (member.active) "#FFFFFF" else "#8995AF", bold = true))
                labels.addView(text(if (member.active) "● Active member" else "○ Inactive member", 10.7f, if (member.active) "#69CBAA" else "#7381A2"))
                labels.addView(text("নিজের share ${moneyText(ownShare)} • দিয়েছেন ${moneyText(paidNow)}", 11f, "#99A6C5").apply { setPadding(0, dp(4), 0, 0) })
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(text(status, 12.2f, if (ownShare > 0.005 && ownDue < 0.005) "#70D7B0" else if (ownDue > 0.005) "#FF9FA4" else "#94A2C0", bold = true))
                c.addView(row)
                c.setOnClickListener { roomMemberActions(member) }
                root.addView(c)
                if (index < members.lastIndex) root.addView(space(7))
            }
        }

        fun addExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>, monthItems: List<RoomExpense>) {
            root.addView(space(22))
            root.addView(text(titleText, 16f, "#B8C4DF", bold = true))
            val monthTotal = total(monthItems)
            val monthPaidLocal = paid(monthItems)
            val monthDueLocal = due(monthItems)
            val line = when {
                monthTotal < 0.005 -> "এই মাসে কোনো হিসাব নেই"
                monthDueLocal < 0.005 -> "✓ SUCCESSFUL • মোট ${moneyText(monthTotal)} সম্পূর্ণ পরিশোধ"
                else -> "মোট ${moneyText(monthTotal)} • Paid ${moneyText(monthPaidLocal)} • Pending ${moneyText(monthDueLocal)}"
            }
            root.addView(text("$subtitleText\n$line", 11f, if (monthTotal > 0.005 && monthDueLocal < 0.005) "#6ED6AF" else "#8291B4", bold = monthTotal > 0.005).apply { setPadding(0, dp(3), 0, dp(8)) })
            if (items.isEmpty()) {
                root.addView(emptyCard("এখনও কোনো হিসাব নেই", subtitleText))
                return
            }
            items.take(12).forEachIndexed { expenseIndex, item ->
                val collection = financeRoom.expenseCollection(item)
                val fullyPaid = collection.third < 0.005
                val participantMembers = item.participantIds.mapNotNull { id -> allRoomMembers.firstOrNull { it.id == id } }
                val c = card(if (fullyPaid) "#16302B" else "#191F34", padding = 13)
                val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                left.addView(text(item.title, 16f, "#FFFFFF", bold = true))
                left.addView(text("মোট ${moneyText(item.amount)} • ${participantMembers.size} জন • জনপ্রতি ${moneyText(item.shareAmount())}", 11.5f, "#E4BC7E").apply { setPadding(0, dp(3), 0, 0) })
                left.addView(text("Paid ${moneyText(collection.second)} • Pending ${moneyText(collection.third)}", 10.8f, if (fullyPaid) "#70D7B0" else "#FFAAAE", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                header.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(text(if (fullyPaid) "✓ COMPLETE" else "PENDING", 12f, if (fullyPaid) "#70D7B0" else "#F0C17A", bold = true))
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
                    c.addView(text(memberLine, 11.2f, if (memberDue < 0.005) "#70D7B0" else if (memberPaid > 0.005) "#F0C17A" else "#FF9FA4", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                }
                if (fullyPaid) c.addView(text("✓ এই হিসাব সম্পূর্ণ পরিশোধ হয়েছে", 11.5f, "#70D7B0", bold = true).apply { setPadding(0, dp(8), 0, 0) })
                c.addView(text("${friendlyDate(item.date)}${if (item.note.isNotBlank()) " • ${item.note}" else ""}", 10.6f, "#7483A6").apply { setPadding(0, dp(7), 0, 0) })
                c.setOnClickListener { roomExpenseActions(item) }
                root.addView(c)
                if (expenseIndex < minOf(11, items.lastIndex)) root.addView(space(8))
            }
        }

        addExpenseSection("🏠 Room Rent", "Member select/unselect করে যাদের মধ্যে ভাড়া ভাগ হবে ঠিক করুন।", rentExpenses, monthRent)
        addExpenseSection("🛒 Mess / বাজার", "বাজারে যারা অংশ নিয়েছে শুধু তাদের select করুন।", messExpenses, monthMess)
        if (otherExpenses.isNotEmpty() || monthOther.isNotEmpty()) addExpenseSection("💡 অন্যান্য Bill", "বিদ্যুৎ, ইন্টারনেট, গ্যাস, পানি ও অন্যান্য shared bill।", otherExpenses, monthOther)

        val rentMonths = rentExpenses.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it })
        if (rentMonths.isNotEmpty()) {
            root.addView(space(22))
            root.addView(text("📅 মাসভিত্তিক Room Rent History", 16f, "#B8C4DF", bold = true))
            root.addView(text("প্রতি মাসের মোট, Paid ও Pending অটোমেটিক সেভ থাকে এবং cloud backup-এর সাথেও থাকে।", 10.8f, "#7E8CAF").apply { setPadding(0, dp(3), 0, dp(8)) })
            rentMonths.entries.take(12).forEach { entry ->
                val mt = total(entry.value)
                val mpd = paid(entry.value)
                val md = due(entry.value)
                val c = card(if (md < 0.005) "#16302B" else "#17213A", padding = 12)
                c.addView(text(entry.key, 14.5f, "#FFFFFF", bold = true))
                c.addView(text("Room Rent ${moneyText(mt)} • Paid ${moneyText(mpd)} • Pending ${moneyText(md)}", 11.5f, if (md < 0.005) "#70D7B0" else "#AEB9D2", bold = true).apply { setPadding(0, dp(4), 0, 0) })
                if (md < 0.005) c.addView(text("✓ PAYMENT SUCCESSFUL", 10.8f, "#70D7B0", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                root.addView(c)
                root.addView(space(7))
            }
        }

'''
    new_money = money[:room_start] + room_tail + return_marker + '    }'
    ms = ms[:start] + new_money + ms[end:]

    new_add = r'''    private fun addRoomExpense(existing: RoomExpense? = null, defaultKind: String? = null) {
        val finance = FinanceStore(this)
        if (existing != null && finance.paymentsForExpense(existing.id).isNotEmpty()) {
            Toast.makeText(this, "এই হিসাবের payment শুরু হয়েছে। নিরাপত্তার জন্য payment থাকা অবস্থায় expense edit করা যাবে না।", Toast.LENGTH_LONG).show()
            return
        }
        val allMembers = finance.roomMembers()
        val visibleMembers = allMembers.filter { !it.deleted && (it.active || existing?.participantIds?.contains(it.id) == true) }
        if (visibleMembers.isEmpty()) {
            Toast.makeText(this, "আগে অন্তত ১ জন Active member যোগ করুন", Toast.LENGTH_LONG).show()
            return
        }
        val kindLabels = listOf("রুম ভাড়া", "বাজার / Mess", "বিদ্যুৎ", "ইন্টারনেট", "গ্যাস", "পানি", "অন্যান্য")
        val kindValues = listOf(FinanceStore.KIND_RENT, FinanceStore.KIND_MARKET, FinanceStore.KIND_ELECTRICITY, FinanceStore.KIND_INTERNET, FinanceStore.KIND_GAS, FinanceStore.KIND_WATER, FinanceStore.KIND_OTHER)
        val box = formBox()
        box.addView(text("খরচের ধরন", 12f, "#9AA8C8", bold = true))
        val kindSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kindLabels)
            setSelection(kindValues.indexOf(existing?.kind ?: defaultKind ?: FinanceStore.KIND_RENT).coerceAtLeast(0))
        }
        val titleInput = input("নাম / বিস্তারিত").apply { setText(existing?.title ?: "") }
        val amountInput = input("মোট টাকা").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.amount.toString())
        }
        val dateInput = input("তারিখ (YYYY-MM-DD)").apply { setText(existing?.date ?: store.today()) }
        val noteInput = input("নোট (ঐচ্ছিক)").apply { setText(existing?.note ?: "") }
        box.addView(kindSpinner); box.addView(space(8)); box.addView(titleInput); box.addView(space(8)); box.addView(amountInput); box.addView(space(10))
        box.addView(text("কারা এই হিসাবের ভাগ নেবে", 12f, "#AAB6D2", bold = true))
        box.addView(text("✓ Member select/unselect করতে পারবেন। Save করার সময় কাউকেই Paid ধরা হবে না।", 10.8f, "#F0C17A", bold = true).apply { setPadding(0, dp(3), 0, dp(4)) })
        val checks = visibleMembers.map { member ->
            member to CheckBox(this).apply {
                text = member.name
                setTextColor(Color.WHITE)
                isChecked = existing?.participantIds?.contains(member.id) ?: member.active
            }
        }
        checks.forEach { box.addView(it.second) }
        box.addView(space(8)); box.addView(dateInput); box.addView(space(8)); box.addView(noteInput)
        kindSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (existing == null && titleInput.text.toString().isBlank()) titleInput.setText(kindLabels[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Room/Mess হিসাব যোগ করুন" else "হিসাব এডিট")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("সেভ") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                val selected = checks.filter { it.second.isChecked }.map { it.first.id }.toSet()
                if (amount <= 0.0 || selected.isEmpty()) {
                    Toast.makeText(this, "সঠিক টাকা এবং অন্তত ১ জন member select করুন", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val kind = kindValues[kindSpinner.selectedItemPosition]
                val item = RoomExpense(
                    existing?.id ?: java.util.UUID.randomUUID().toString(),
                    titleInput.text.toString().trim().ifBlank { kindLabels[kindSpinner.selectedItemPosition] },
                    amount,
                    "",
                    selected,
                    dateInput.text.toString().trim().ifBlank { store.today() },
                    noteInput.text.toString().trim(),
                    kind
                )
                val items = finance.roomExpenses()
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx >= 0) items[idx] = item else items.add(item)
                finance.saveRoomExpenses(items)
                CloudSyncManager.scheduleUpload(this)
                Toast.makeText(this, "হিসাব সেভ হয়েছে • ${selected.size} জন • জনপ্রতি ${moneyText(item.shareAmount())} • Paid 0", Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

'''
    ms = replace_function(ms, '    private fun addRoomExpense(existing: RoomExpense? = null, defaultKind: String? = null) {', new_add)

    new_actions = r'''    private fun roomMemberActions(item: RoomMember) {
        val options = mutableListOf("✓ এই মাসের Payment Complete করুন", "নাম/স্ট্যাটাস এডিট করুন", if (item.active) "Inactive করুন" else "Active করুন", "Delete member")
        AlertDialog.Builder(this).setTitle(item.name).setItems(options.toTypedArray()) { _, which ->
            when (which) {
                0 -> completeMemberMonthPayment(item)
                1 -> addRoomMember(item)
                2 -> {
                    val finance = FinanceStore(this)
                    val items = finance.roomMembers()
                    val index = items.indexOfFirst { it.id == item.id }
                    if (index >= 0) items[index] = item.copy(active = !item.active)
                    finance.saveRoomMembers(items)
                    CloudSyncManager.scheduleUpload(this)
                    render()
                }
                3 -> confirmDeleteRoomMember(item)
            }
        }.show()
    }

'''
    ms = replace_function(ms, '    private fun roomMemberActions(item: RoomMember) {', new_actions)

    anchor = '    private fun addRoomExpense(existing: RoomExpense? = null, defaultKind: String? = null) {'
    helper = r'''    private fun completeMemberMonthPayment(member: RoomMember) {
        val finance = FinanceStore(this)
        val month = LocalDate.now().toString().substring(0, 7)
        val pending = finance.roomExpenses().filter { it.date.startsWith(month) && member.id in it.participantIds && finance.remainingForExpense(it, member.id) > 0.005 }
        if (pending.isEmpty()) {
            Toast.makeText(this, "✓ ${member.name}-এর এই মাসের সব payment complete", Toast.LENGTH_LONG).show()
            return
        }
        val totalDue = pending.sumOf { finance.remainingForExpense(it, member.id) }
        val labels = mutableListOf("✓ সব বাকি একসাথে Complete • ${moneyText(totalDue)}")
        labels.addAll(pending.map { "${it.title} • বাকি ${moneyText(finance.remainingForExpense(it, member.id))}" })
        AlertDialog.Builder(this).setTitle("${member.name} • Payment Complete")
            .setItems(labels.toTypedArray()) { _, which ->
                val targets = if (which == 0) pending else listOf(pending[which - 1])
                val amount = targets.sumOf { finance.remainingForExpense(it, member.id) }
                AlertDialog.Builder(this).setTitle("পেমেন্ট নিশ্চিত করুন")
                    .setMessage("${member.name} • ${moneyText(amount)} Payment Complete করবেন?")
                    .setPositiveButton("✓ Complete") { _, _ ->
                        val payments = finance.roomExpensePayments()
                        targets.forEach { expense ->
                            val remaining = finance.remainingForExpense(expense, member.id)
                            if (remaining > 0.005) payments.add(RoomExpensePayment(expenseId = expense.id, memberId = member.id, toMemberId = "", amount = remaining, date = store.today(), note = "Payment complete"))
                        }
                        finance.saveRoomExpensePayments(payments)
                        CloudSyncManager.scheduleUpload(this)
                        Toast.makeText(this, "✓ ${member.name} • ${moneyText(amount)} PAYMENT COMPLETE", Toast.LENGTH_LONG).show()
                        render()
                    }
                    .setNegativeButton("বাতিল", null).show()
            }.setNegativeButton("বন্ধ", null).show()
    }

    private fun shareRoomMessPdf() {
        runCatching {
            val dir = java.io.File(cacheDir, "shared_reports").apply { mkdirs() }
            val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
            val file = java.io.File(dir, "Guide-Room-Mess-$stamp.pdf")
            GuidePdfReport.writeRoomMessFile(this, store, file)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Guide Room/Mess হিসাব")
                putExtra(android.content.Intent.EXTRA_TEXT, "Room/Mess হিসাব PDF")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, "PDF WhatsApp / অন্য অ্যাপে শেয়ার করুন"))
        }.onFailure {
            Toast.makeText(this, "PDF share করা যায়নি: ${it.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

'''
    require(ms, anchor, 'addRoomExpense helper anchor')
    ms = ms.replace(anchor, helper + anchor, 1)

    # Everyone with a due share is eligible; there is no mandatory original payer now.
    ms = ms.replace('it.id != item.paidById && finance.remainingForExpense(item, it.id) > 0.005', 'finance.remainingForExpense(item, it.id) > 0.005')

    # New member payments are contributions to the bill, not money owed to a fake payer.
    ms = ms.replace('toMemberId = item.paidById,', 'toMemberId = "",')
    mp.write_text(ms)
    print('v3.24 simple member-select, no-fake-payer, completion and PDF-share UI applied')
else:
    print('v3.24 MainActivity patch already applied')


# Add a Room/Mess PDF generator to the existing PDF report engine.
pp = Path('app/src/main/java/com/guide/app/PdfReportsActivity.kt')
ps = pp.read_text()
if 'fun writeRoomMessFile(' not in ps:
    anchor = '    private fun writeAttendance(w: PdfWriter, store: GuideStore) {'
    require(ps, anchor, 'GuidePdfReport writeAttendance anchor')
    method = r'''    fun writeRoomMessFile(context: android.content.Context, store: GuideStore, file: java.io.File) {
        val doc = PdfDocument()
        val writer = PdfWriter(doc, store.profileName())
        writeRoomMess(writer, context)
        writer.finish()
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun writeRoomMess(w: PdfWriter, context: android.content.Context) {
        val finance = FinanceStore(context)
        val currency = context.getSharedPreferences("guide_ui", android.content.Context.MODE_PRIVATE).getString("currency", "SAR") ?: "SAR"
        val expenses = finance.roomExpenses()
        val members = finance.roomMembers().filter { !it.deleted }
        w.heading("Room / Mess হিসাব রিপোর্ট")
        w.info("Member ${members.size} জন • Room Rent, Mess/বাজার ও shared bill-এর payment status")
        w.rule()
        if (expenses.isEmpty()) {
            w.info("কোনো Room/Mess হিসাব নেই।")
            return
        }
        val months = expenses.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it })
        months.forEach { (month, items) ->
            val rent = items.filter { it.kind == FinanceStore.KIND_RENT }
            val mess = items.filter { it.kind == FinanceStore.KIND_MARKET }
            val other = items.filter { it.kind != FinanceStore.KIND_RENT && it.kind != FinanceStore.KIND_MARKET }
            fun total(list: List<RoomExpense>) = list.sumOf { it.amount }
            fun paid(list: List<RoomExpense>) = list.sumOf { finance.expenseCollection(it).second }
            fun due(list: List<RoomExpense>) = list.sumOf { finance.expenseCollection(it).third }
            w.heading(month)
            w.info("Room Rent $currency ${money(total(rent))} • Paid ${money(paid(rent))} • Pending ${money(due(rent))}")
            w.info("Mess/বাজার $currency ${money(total(mess))} • Paid ${money(paid(mess))} • Pending ${money(due(mess))}")
            if (other.isNotEmpty()) w.info("Other Bill $currency ${money(total(other))} • Paid ${money(paid(other))} • Pending ${money(due(other))}")
            w.rule()
            items.forEach { expense ->
                val collection = finance.expenseCollection(expense)
                val kind = finance.kindLabel(expense.kind)
                val state = if (collection.third < 0.005) "PAYMENT COMPLETE" else "Pending $currency ${money(collection.third)}"
                w.row(expense.date, "$kind • ${expense.title} • Total $currency ${money(expense.amount)} • Paid ${money(collection.second)} • $state")
                expense.participantIds.forEach { id ->
                    val member = finance.roomMembers().firstOrNull { it.id == id }
                    if (member != null) {
                        val memberPaid = finance.paidForExpense(expense, id)
                        val memberDue = finance.remainingForExpense(expense, id)
                        val memberState = if (memberDue < 0.005) "COMPLETE" else "Due $currency ${money(memberDue)}"
                        w.row("", "${member.name} • Share $currency ${money(expense.shareAmount())} • Paid ${money(memberPaid)} • $memberState")
                    }
                }
            }
            w.sectionBreak()
        }
    }

'''
    ps = ps.replace(anchor, method + anchor, 1)
    pp.write_text(ps)
    print('v3.24 Room/Mess PDF report writer applied')
else:
    print('v3.24 PDF writer already applied')


# FileProvider for temporary PDF sharing to WhatsApp/other apps.
manifest = Path('app/src/main/AndroidManifest.xml')
mx = manifest.read_text()
if '.fileprovider' not in mx:
    provider = '''\n        <provider\n            android:name="androidx.core.content.FileProvider"\n            android:authorities="${applicationId}.fileprovider"\n            android:exported="false"\n            android:grantUriPermissions="true">\n            <meta-data\n                android:name="android.support.FILE_PROVIDER_PATHS"\n                android:resource="@xml/file_paths" />\n        </provider>\n'''
    require(mx, '    </application>', 'manifest application close')
    mx = mx.replace('    </application>', provider + '    </application>', 1)
    manifest.write_text(mx)
    print('v3.24 FileProvider manifest applied')

paths = Path('app/src/main/res/xml/file_paths.xml')
paths.parent.mkdir(parents=True, exist_ok=True)
if not paths.exists():
    paths.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<paths xmlns:android="http://schemas.android.com/apk/res/android">\n    <cache-path name="shared_reports" path="shared_reports/" />\n</paths>\n''')
    print('v3.24 file_paths.xml created')

# Version metadata.
bp = Path('app/build.gradle.kts')
bs = bp.read_text()
if 'versionName = "3.23.0"' in bs:
    bs = bs.replace('versionCode = 36', 'versionCode = 37', 1)
    bs = bs.replace('versionName = "3.23.0"', 'versionName = "3.24.0"', 1)
    bp.write_text(bs)
    print('v3.24 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text()
updated = re.sub(r'("appVersion"\s+to\s+")[0-9.]+("\s*\))', r'\g<1>3.24.0\2', cs, count=1)
if updated != cs:
    cp.write_text(updated)
    print('v3.24 cloud metadata applied')
