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
# FinanceStore archive model.
# Completed expenses stay visible until the user explicitly presses Clear.
# Clear only archives the expense ID: expense/member/payment data is preserved
# inside guide_store and therefore remains part of Firebase snapshot backup.
# ---------------------------------------------------------------------------
fp = Path('app/src/main/java/com/guide/app/FinanceStore.kt')
fs = fp.read_text()
if 'GuideExpenseArchiveV328' not in fs:
    const_anchor = '        private const val SETTLEMENTS_KEY = "room_settlements_v321"\n'
    require(fs, const_anchor, 'FinanceStore constants')
    fs = fs.replace(
        const_anchor,
        const_anchor + '        private const val ARCHIVED_EXPENSES_KEY_V328 = "room_expense_archived_v328"\n        private const val ARCHIVE_MIGRATED_KEY_V328 = "room_expense_archive_migrated_v328"\n',
        1,
    )

    method_anchor = '    fun roomSettlements(): MutableList<RoomSettlement> {'
    require(fs, method_anchor, 'FinanceStore roomSettlements')
    archive_methods = r'''    // GuideExpenseArchiveV328
    fun archivedExpenseIdsV328(): Set<String> =
        (prefs.getStringSet(ARCHIVED_EXPENSES_KEY_V328, emptySet()) ?: emptySet()).toSet()

    fun isExpenseArchivedV328(expenseId: String): Boolean =
        expenseId.isNotBlank() && expenseId in archivedExpenseIdsV328()

    fun archiveExpenseV328(expenseId: String) {
        if (expenseId.isBlank()) return
        val ids = archivedExpenseIdsV328().toMutableSet()
        if (ids.add(expenseId)) {
            prefs.edit().putStringSet(ARCHIVED_EXPENSES_KEY_V328, ids.toSet()).apply()
        }
    }

    fun restoreExpenseFromHistoryV328(expenseId: String) {
        val ids = archivedExpenseIdsV328().toMutableSet()
        if (ids.remove(expenseId)) {
            prefs.edit().putStringSet(ARCHIVED_EXPENSES_KEY_V328, ids.toSet()).apply()
        }
    }

    /**
     * v3.26/v3.27 automatically treated every zero-due expense as History.
     * On the first v3.28 launch we preserve that existing state once. New
     * completed expenses are not archived until the user presses Clear.
     */
    fun migrateCompletedExpensesToArchiveV328() {
        if (prefs.getBoolean(ARCHIVE_MIGRATED_KEY_V328, false)) return
        val ids = archivedExpenseIdsV328().toMutableSet()
        roomExpenses().filter { it.amount > 0.005 && expenseCollection(it).third <= 0.005 }
            .forEach { ids.add(it.id) }
        prefs.edit()
            .putStringSet(ARCHIVED_EXPENSES_KEY_V328, ids.toSet())
            .putBoolean(ARCHIVE_MIGRATED_KEY_V328, true)
            .apply()
    }

'''
    fs = fs.replace(method_anchor, archive_methods + method_anchor, 1)
    fp.write_text(fs)
    print('v3.28 FinanceStore explicit archive model applied')
else:
    print('v3.28 FinanceStore archive model already applied')


# ---------------------------------------------------------------------------
# MainActivity per-expense payment groups.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()
if 'GuideExpenseGroupsV328' not in ms:
    # Preserve all v3.26/v3.27 completed history on the first upgrade, then use
    # the explicit archived-ID set for live/history separation.
    expense_anchor = '        val allExpenses = financeRoom.roomExpenses()\n        val allPayments = financeRoom.roomExpensePayments()\n'
    require(ms, expense_anchor, 'v3.27 finance expense anchor')
    ms = ms.replace(
        expense_anchor,
        '        financeRoom.migrateCompletedExpensesToArchiveV328()\n        val allExpenses = financeRoom.roomExpenses()\n        val allPayments = financeRoom.roomExpensePayments()\n        val archivedExpenseIds = financeRoom.archivedExpenseIdsV328()\n',
        1,
    )

    old_active = '''        // ACTIVE is based on remaining money, not the calendar month. As soon as
        // an expense reaches zero due it automatically disappears from the live
        // dashboard and remains permanently available in History.
        val activeExpenses = allExpenses.filter { it.amount > 0.005 && roomDue(it) > 0.005 }
        val completedExpenses = allExpenses.filter { it.amount > 0.005 && roomDue(it) <= 0.005 }'''
    new_active = '''        // GuideExpenseGroupsV328
        // Live = not explicitly cleared. A fully paid card stays visible with a
        // Clear → History button until the user confirms the archive action.
        val activeExpenses = allExpenses.filter { it.amount > 0.005 && it.id !in archivedExpenseIds }
        val completedExpenses = allExpenses.filter { it.amount > 0.005 && it.id in archivedExpenseIds }'''
    require(ms, old_active, 'v3.26 active/completed split')
    ms = ms.replace(old_active, new_active, 1)

    # Remove the global combined member-payment list. Payments are now shown and
    # controlled under each individual expense, so unrelated bills never mix.
    member_start = ms.find('        // GuideFinanceSidebarHistoryV327\n')
    member_end = ms.find('        fun addActiveExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>) {', member_start)
    if member_start < 0 or member_end < 0:
        raise SystemExit('v3.27 global member block not found')
    replacement_note = r'''        // GuideExpenseGroupsV328
        root.addView(space(18))
        root.addView(text("Payment Group", 11f, "#7F8EAE", bold = true).apply { letterSpacing = 0.08f })
        root.addView(text("প্রতিটি খরচের নিচে শুধু সেই খরচে নির্বাচিত member-দের আলাদা box থাকবে। Member box ট্যাপ করেই ওই হিসাবের partial/full payment control করা যাবে।", 11f, "#8796B9").apply { setPadding(0, dp(3), 0, dp(2)) })

'''
    ms = ms[:member_start] + replacement_note + ms[member_end:]

    new_section = r'''        fun addActiveExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>) {
            root.addView(space(22))
            root.addView(text(titleText, 16f, "#B8C4DF", bold = true))
            val t = total(items)
            val p = paid(items)
            val d = due(items)
            root.addView(text(if (items.isEmpty()) "$subtitleText\nএখন কোনো চলমান হিসাব নেই" else "$subtitleText\nমোট ${moneyText(t)} • Paid ${moneyText(p)} • Pending ${moneyText(d)}", 11f, if (items.isEmpty()) "#7F8EAE" else "#8291B4", bold = items.isNotEmpty()).apply { setPadding(0, dp(3), 0, dp(8)) })
            if (items.isEmpty()) {
                root.addView(emptyCard("এখন কোনো চলমান হিসাব নেই", "নতুন হিসাব যোগ করলে এখানে আলাদা Payment Group হিসেবে দেখা যাবে।"))
                return
            }

            items.sortedByDescending { it.date }.take(30).forEachIndexed { expenseIndex, item ->
                val collection = financeRoom.expenseCollection(item)
                val fullyPaid = collection.third < 0.005
                val participantMembers = item.participantIds.mapNotNull { id -> allRoomMembers.firstOrNull { it.id == id } }
                val c = card(if (fullyPaid) "#15312D" else "#191F34", padding = 13)

                val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                left.addView(text(item.title, 17f, "#FFFFFF", bold = true))
                left.addView(text("${financeRoom.kindLabel(item.kind)} • ${roomHistoryFullDate(item.date)}", 10.8f, "#92A1C4", bold = true).apply { setPadding(0, dp(2), 0, 0) })
                left.addView(text("মোট ${moneyText(item.amount)} • ${participantMembers.size} জন • জনপ্রতি ${moneyText(item.shareAmount())}", 11.5f, "#E4BC7E").apply { setPadding(0, dp(4), 0, 0) })
                left.addView(text("Paid ${moneyText(collection.second)} • Pending ${moneyText(collection.third)}", 11f, if (fullyPaid) "#72D8B2" else "#FFAAAE", bold = true).apply { setPadding(0, dp(3), 0, 0) })
                header.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(text(if (fullyPaid) "✓ COMPLETE" else "PENDING", 12f, if (fullyPaid) "#70D7B0" else "#F0C17A", bold = true))
                c.addView(header)

                c.addView(text("এই হিসাবের সদস্য", 10.5f, "#8292B8", bold = true).apply {
                    letterSpacing = 0.06f
                    setPadding(0, dp(12), 0, dp(7))
                })

                participantMembers.forEachIndexed { memberIndex, member ->
                    val memberPaid = financeRoom.paidForExpense(item, member.id)
                    val memberDue = financeRoom.remainingForExpense(item, member.id)
                    val memberComplete = memberDue < 0.005
                    val memberBox = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(11), dp(12), dp(11))
                        background = premiumGradientStroke(if (memberComplete) "#173A33" else "#202A46", if (memberComplete) "#5077D9B3" else "#405C6F9D", 1, 15)
                        elevation = dp(3).toFloat()
                        applyDepthPress(3)
                        setOnClickListener { roomExpenseMemberActionsV328(item, member) }
                    }
                    val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(text(member.name, 14.5f, "#FFFFFF", bold = true))
                    labels.addView(text("Share ${moneyText(item.shareAmount())} • Paid ${moneyText(memberPaid)}", 10.5f, "#AAB6D2").apply { setPadding(0, dp(2), 0, 0) })
                    memberBox.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val status = when {
                        memberComplete -> "✓ COMPLETE"
                        memberPaid > 0.005 -> "◐ বাকি ${moneyText(memberDue)}"
                        else -> "⏳ ${moneyText(memberDue)}"
                    }
                    memberBox.addView(text(status, 11.5f, if (memberComplete) "#72D8B2" else "#FF9FA5", bold = true))
                    c.addView(memberBox)
                    if (memberIndex < participantMembers.lastIndex) c.addView(space(6))
                }

                c.addView(space(10))
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(smallAction("📄 এই হিসাব PDF", "#28765E") { shareRoomExpensePdfV328(item) }, LinearLayout.LayoutParams(0, dp(46), 1f))
                if (fullyPaid) {
                    actions.addView(hSpace(7))
                    actions.addView(smallAction("✓ Clear → History", "#5B4D84") { archiveCompletedExpenseV328(item) }, LinearLayout.LayoutParams(0, dp(46), 1f))
                }
                c.addView(actions)
                if (fullyPaid) {
                    c.addView(text("সব payment complete। Clear চাপলে data delete হবে না—History + Firebase backup-এ থাকবে।", 10.2f, "#80CDB2").apply { setPadding(0, dp(8), 0, 0) })
                }

                root.addView(c)
                if (expenseIndex < items.lastIndex) root.addView(space(9))
            }
        }

'''
    ms = replace_function(ms, '        fun addActiveExpenseSection(titleText: String, subtitleText: String, items: List<RoomExpense>) {', new_section)

    # New expense-scoped member controls and explicit archive/PDF helpers.
    helper_anchor = '    private fun roomMemberActions(item: RoomMember) {'
    require(ms, helper_anchor, 'roomMemberActions anchor')
    helpers = r'''    private fun roomExpenseMemberActionsV328(expense: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val paid = finance.paidForExpense(expense, member.id)
        val due = finance.remainingForExpense(expense, member.id)
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#121C36", "#7E6BFF", 1, 24)
        }
        box.addView(text(member.name, 20f, "#FFFFFF", bold = true))
        box.addView(text("${expense.title} • ${finance.kindLabel(expense.kind)}", 11.5f, "#A2B0D0", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        box.addView(text("Share ${moneyText(expense.shareAmount())} • Paid ${moneyText(paid)} • Pending ${moneyText(due)}", 13f, if (due < 0.005) "#73D8B3" else "#F2C37D", bold = true).apply { setPadding(0, dp(8), 0, dp(10)) })

        if (due > 0.005) {
            box.addView(financePremiumActionRow("✎", "Payment amount লিখুন", "শুধু এই হিসাবের partial/custom payment", "#2C5A79") {
                dialog.dismiss()
                editMemberPaymentAmount(member, expense)
            })
            box.addView(space(8))
            box.addView(financePremiumActionRow("✓", "এই হিসাব Full Complete", "বাকি ${moneyText(due)} একবারে complete করুন", "#246856") {
                dialog.dismiss()
                completeExpenseMemberPaymentV328(expense, member)
            })
        } else {
            val done = card("#173A33", padding = 12)
            done.addView(text("✓ PAYMENT COMPLETE", 14f, "#73D8B3", bold = true))
            done.addView(text("এই member-এর এই হিসাব সম্পূর্ণ পরিশোধ হয়েছে।", 10.5f, "#9FBDAF").apply { setPadding(0, dp(3), 0, 0) })
            box.addView(done)
        }

        box.addView(space(10))
        box.addView(financePremiumActionRow("✦", "Member নাম / স্ট্যাটাস এডিট", "পুরোনো payment/history একই ID-তে থাকবে", "#4F4676") {
            dialog.dismiss()
            addRoomMember(member)
        })
        box.addView(space(8))
        box.addView(financePremiumActionRow(if (member.active) "◌" else "●", if (member.active) "Inactive করুন" else "Active করুন", "বর্তমান history/payment নষ্ট হবে না", "#5F4D32") {
            dialog.dismiss()
            val f = FinanceStore(this)
            val items = f.roomMembers()
            val index = items.indexOfFirst { it.id == member.id }
            if (index >= 0) items[index] = member.copy(active = !member.active)
            f.saveRoomMembers(items)
            CloudSyncManager.scheduleUpload(this)
            render()
        })
        box.addView(space(8))
        box.addView(financePremiumActionRow("⌫", "Delete member", "নাম active list থেকে যাবে; পুরোনো expense/history/payment backup থাকবে", "#733B49") {
            dialog.dismiss()
            deleteRoomMember(member)
        })
        box.addView(space(12))
        box.addView(smallAction("বন্ধ", "#313C59") { dialog.dismiss() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun completeExpenseMemberPaymentV328(expense: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val due = finance.remainingForExpense(expense, member.id)
        if (due < 0.005) {
            Toast.makeText(this, "✓ ${member.name} • PAYMENT COMPLETE", Toast.LENGTH_LONG).show()
            return
        }
        val payments = finance.roomExpensePayments()
        payments.add(RoomExpensePayment(
            expenseId = expense.id,
            memberId = member.id,
            toMemberId = "",
            amount = due,
            date = store.today(),
            note = "Payment complete"
        ))
        finance.saveRoomExpensePayments(payments)
        CloudSyncManager.scheduleUpload(this)
        Toast.makeText(this, "✓ ${member.name} • ${moneyText(due)} PAYMENT COMPLETE", Toast.LENGTH_LONG).show()
        render()
    }

    private fun archiveCompletedExpenseV328(expense: RoomExpense) {
        val finance = FinanceStore(this)
        val due = finance.expenseCollection(expense).third
        if (due > 0.005) {
            Toast.makeText(this, "Pending ${moneyText(due)} আছে—সব payment complete হলে Clear করা যাবে", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("✓ হিসাব Clear করুন")
            .setMessage("${expense.title}\nমোট ${moneyText(expense.amount)}\n\nClear করলে active page থেকে সরে History-তে যাবে। Expense, member, payment ও PDF data delete হবে না।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Clear → History") { _, _ ->
                finance.archiveExpenseV328(expense.id)
                CloudSyncManager.scheduleUpload(this)
                Toast.makeText(this, "✓ History-তে সংরক্ষণ হয়েছে", Toast.LENGTH_LONG).show()
                render()
            }
            .show()
    }

    private fun shareRoomExpensePdfV328(expense: RoomExpense) {
        runCatching {
            val dir = java.io.File(cacheDir, "shared_reports").apply { mkdirs() }
            val safe = expense.title.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "Hisab" }
            val file = java.io.File(dir, "Guide-$safe-${expense.date}.pdf")
            GuidePdfReport.writeRoomExpenseFileV328(this, store, file, expense.id)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "${expense.title} হিসাব")
                putExtra(android.content.Intent.EXTRA_TEXT, "${expense.title} • ${roomHistoryFullDate(expense.date)} • Guide হিসাব PDF")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, "PDF শেয়ার করুন"))
        }.onFailure {
            Toast.makeText(this, "PDF তৈরি/শেয়ার করা যায়নি", Toast.LENGTH_LONG).show()
        }
    }

'''
    ms = ms.replace(helper_anchor, helpers + helper_anchor, 1)

    # Sidebar History now shows only explicitly cleared/archived expenses.
    history_filter_old = '''        val expenses = finance.roomExpenses().filter {
            it.amount > 0.005 && finance.expenseCollection(it).third <= 0.005
        }'''
    history_filter_new = '''        val archivedIds = finance.archivedExpenseIdsV328()
        val expenses = finance.roomExpenses().filter {
            it.amount > 0.005 && it.id in archivedIds
        }'''
    require(ms, history_filter_old, 'history filter')
    ms = ms.replace(history_filter_old, history_filter_new, 1)

    mp.write_text(ms)
    print('v3.28 per-expense payment groups + clear-to-history UI applied')
else:
    print('v3.28 MainActivity patch already applied')


# ---------------------------------------------------------------------------
# PDF: one expense = one shareable PDF; month History PDF = archived only.
# ---------------------------------------------------------------------------
pp = Path('app/src/main/java/com/guide/app/PdfReportsActivity.kt')
ps = pp.read_text()
if 'writeRoomExpenseFileV328' not in ps:
    month_filter_old = '''        val expenses = finance.roomExpenses().filter {
            it.date.startsWith(monthKey) && it.amount > 0.005 && finance.expenseCollection(it).third <= 0.005
        }'''
    month_filter_new = '''        val archivedIds = finance.archivedExpenseIdsV328()
        val expenses = finance.roomExpenses().filter {
            it.date.startsWith(monthKey) && it.amount > 0.005 && it.id in archivedIds
        }'''
    require(ps, month_filter_old, 'month history PDF filter')
    ps = ps.replace(month_filter_old, month_filter_new, 1)

    pdf_anchor = '    fun writeRoomMessMonthFile(context: android.content.Context, store: GuideStore, file: java.io.File, monthKey: String) {'
    require(ps, pdf_anchor, 'month PDF function anchor')
    expense_pdf = r'''    fun writeRoomExpenseFileV328(context: android.content.Context, store: GuideStore, file: java.io.File, expenseId: String) {
        val finance = FinanceStore(context)
        val expense = finance.roomExpenses().firstOrNull { it.id == expenseId } ?: error("Expense not found")
        val allMembers = finance.roomMembers()
        val collection = finance.expenseCollection(expense)
        val currency = context.getSharedPreferences("guide_ui", android.content.Context.MODE_PRIVATE).getString("currency", "SAR") ?: "SAR"
        val doc = PdfDocument()
        val w = PdfWriter(doc, store.profileName())
        val fullDate = runCatching {
            java.time.LocalDate.parse(expense.date).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))
        }.getOrElse { expense.date }
        val complete = collection.third < 0.005

        w.heading("Room / Mess হিসাব")
        w.info("${finance.kindLabel(expense.kind)} • ${expense.title} • $fullDate")
        w.summaryCard(finance.kindLabel(expense.kind).uppercase(Locale.ENGLISH), currency, expense.amount, collection.second, collection.third)
        w.rule()
        w.expenseCard(
            expense.title,
            "Total $currency ${money(expense.amount)} • ${expense.participantIds.size} member • Share $currency ${money(expense.shareAmount())}",
            if (complete) "✓ PAYMENT COMPLETE" else "Pending $currency ${money(collection.third)}",
            complete
        )
        expense.participantIds.forEach { memberId ->
            val name = allMembers.firstOrNull { it.id == memberId }?.name ?: "Member"
            val paid = finance.paidForExpense(expense, memberId)
            val due = finance.remainingForExpense(expense, memberId)
            w.memberPaymentCard(
                name,
                "Share $currency ${money(expense.shareAmount())} • Paid $currency ${money(paid)} • Pending $currency ${money(due)}",
                if (due < 0.005) "✓ PAYMENT COMPLETE" else if (paid > 0.005) "PARTIAL PAYMENT" else "PAYMENT DUE",
                due < 0.005
            )
        }
        val events = finance.paymentsForExpense(expense.id).sortedBy { it.date }
        if (events.isNotEmpty()) {
            w.rule()
            w.heading("Payment activity")
            events.forEach { payment ->
                val memberName = allMembers.firstOrNull { it.id == payment.memberId }?.name ?: "Member"
                val eventDate = runCatching {
                    java.time.LocalDate.parse(payment.date).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))
                }.getOrElse { payment.date }
                w.row(eventDate, "$memberName • $currency ${money(payment.amount)}${if (payment.note.isNotBlank()) " • ${payment.note}" else ""}")
            }
        }
        w.finish()
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

'''
    ps = ps.replace(pdf_anchor, expense_pdf + pdf_anchor, 1)
    pp.write_text(ps)
    print('v3.28 individual expense PDF + archived-only month PDF applied')
else:
    print('v3.28 PDF patch already applied')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 41', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.28.0"', gs, count=1)
gp.write_text(gs)
print('v3.28 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text().replace('3.27.0', '3.28.0')
    cp.write_text(cs)
    print('v3.28 cloud metadata applied')
