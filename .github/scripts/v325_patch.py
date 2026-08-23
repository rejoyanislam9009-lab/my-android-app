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
# Guide v3.25
# - Partial/custom amount member payments.
# - Premium member action/payment dialogs.
# - Preserve main content scroll position on same-page re-render.
# - More polished Room/Mess PDF report.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideFinancePremiumV325' not in ms:
    # Keep the user's exact position when an action re-renders the same screen.
    field_anchor = '    private var pendingRingtoneResult: ((String) -> Unit)? = null\n'
    require(ms, field_anchor, 'MainActivity field anchor')
    ms = ms.replace(
        field_anchor,
        field_anchor + '    private var guideContentScroll: ScrollView? = null\n    private var lastRenderedPageKey: String? = null\n',
        1,
    )

    new_render = r'''    // GuideFinancePremiumV325
    private fun render() {
        val pageKey = "$currentTab:${detailPage ?: "root"}"
        val previousPageKey = lastRenderedPageKey
        val previousScrollY = guideContentScroll?.scrollY ?: 0

        handler.removeCallbacksAndMessages(null)
        alarmCountdownViews.clear()
        setContentView(buildShell())
        lastRenderedPageKey = pageKey

        // Actions such as payment/edit/delete rebuild the screen. Restore the
        // previous Y only when the user is still on the same page; navigation
        // to another page continues to start normally from the top.
        if (previousPageKey == pageKey && previousScrollY > 0) {
            guideContentScroll?.post {
                guideContentScroll?.scrollTo(0, previousScrollY)
            }
        }

        if (currentTab == "home" && detailPage == null) startClock()
        if (detailPage == "alarms") startAlarmCountdowns()
    }

'''
    ms = replace_function(ms, '    private fun render() {', new_render)

    old_scroll = '''        shell.addView(ScrollView(this).apply {\n            isFillViewport = true\n            addView(body)\n        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))'''
    new_scroll = '''        val mainContentScroll = ScrollView(this).apply {\n            isFillViewport = true\n            isVerticalScrollBarEnabled = false\n            addView(body)\n        }\n        guideContentScroll = mainContentScroll\n        shell.addView(mainContentScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))'''
    require(ms, old_scroll, 'main content ScrollView')
    ms = ms.replace(old_scroll, new_scroll, 1)

    # Premium member action dialog instead of Android's plain grey setItems list.
    new_member_actions = r'''    private fun roomMemberActions(item: RoomMember) {
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#131D38", "#806CFF", 1, 24)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = text(item.name.trim().take(1).uppercase().ifBlank { "M" }, 17f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = premiumGradientStroke("#6253E8", "#B7A9FF", 1, 15)
            elevation = dp(5).toFloat()
        }
        header.addView(badge, LinearLayout.LayoutParams(dp(46), dp(46)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
        titles.addView(text(item.name, 20f, "#FFFFFF", bold = true))
        titles.addView(text(if (item.active) "● Active Room/Mess member" else "○ Inactive Room/Mess member", 11f, if (item.active) "#6FD5AE" else "#8E9AB8", bold = true))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(header)
        box.addView(text("PAYMENT & MEMBER CONTROL", 9.5f, "#7E8DB5", bold = true).apply {
            letterSpacing = 0.09f
            setPadding(0, dp(14), 0, dp(6))
        })

        box.addView(financePremiumActionRow("✎", "Payment amount লিখুন", "Full payment না হলে নিজের মতো amount সেভ করুন", "#2C5A79") {
            dialog.dismiss()
            chooseMemberPartialPayment(item)
        })
        box.addView(space(8))
        box.addView(financePremiumActionRow("✓", "এই মাসের সব বাকি Complete", "সব pending share একসাথে সম্পূর্ণ পরিশোধ", "#246856") {
            dialog.dismiss()
            completeMemberMonthPayment(item)
        })
        box.addView(space(8))
        box.addView(financePremiumActionRow("✦", "নাম / স্ট্যাটাস এডিট করুন", "Member-এর নাম বা তথ্য পরিবর্তন করুন", "#4F4676") {
            dialog.dismiss()
            addRoomMember(item)
        })
        box.addView(space(8))
        box.addView(financePremiumActionRow(if (item.active) "◌" else "●", if (item.active) "Inactive করুন" else "Active করুন", if (item.active) "হিসাব history থাকবে, নতুন ভাগে থাকবে না" else "আবার নতুন হিসাবের member হিসেবে ব্যবহার করুন", "#5F4D32") {
            dialog.dismiss()
            val finance = FinanceStore(this)
            val items = finance.roomMembers()
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) items[index] = item.copy(active = !item.active)
            finance.saveRoomMembers(items)
            CloudSyncManager.scheduleUpload(this)
            render()
        })
        box.addView(space(8))
        box.addView(financePremiumActionRow("⌫", "Delete member", "পুরোনো হিসাব/history নষ্ট হবে না", "#733B49") {
            dialog.dismiss()
            deleteRoomMember(item)
        })
        box.addView(space(12))
        val close = Button(this).apply {
            text = "বন্ধ"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = premiumGradientStroke("#27324F", "#5E6F9F", 1, 14)
            setOnClickListener { dialog.dismiss() }
        }
        box.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

'''
    ms = replace_function(ms, '    private fun roomMemberActions(item: RoomMember) {', new_member_actions)

    # Premium full-completion confirmation; partial/custom amount lives beside it.
    new_complete = r'''    private fun completeMemberMonthPayment(member: RoomMember) {
        val finance = FinanceStore(this)
        val month = LocalDate.now().toString().substring(0, 7)
        val pending = finance.roomExpenses().filter {
            it.date.startsWith(month) && member.id in it.participantIds && finance.remainingForExpense(it, member.id) > 0.005
        }
        if (pending.isEmpty()) {
            Toast.makeText(this, "✓ ${member.name}-এর এই মাসের সব payment complete", Toast.LENGTH_LONG).show()
            return
        }
        val totalDue = pending.sumOf { finance.remainingForExpense(it, member.id) }
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#102B2A", "#58D7AC", 1, 24)
        }
        box.addView(text("✓ Payment Complete", 20f, "#FFFFFF", bold = true))
        box.addView(text(member.name, 14f, "#8FE0C3", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        box.addView(text("মোট বাকি ${moneyText(totalDue)}", 25f, "#F2C77F", bold = true).apply { setPadding(0, dp(12), 0, dp(10)) })
        pending.forEach { expense ->
            val due = finance.remainingForExpense(expense, member.id)
            val line = card("#173A37", padding = 11)
            line.addView(text(expense.title, 13.5f, "#FFFFFF", bold = true))
            line.addView(text("${finance.kindLabel(expense.kind)} • বাকি ${moneyText(due)}", 11f, "#9CCFBE").apply { setPadding(0, dp(3), 0, 0) })
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
                if (remaining > 0.005) {
                    payments.add(RoomExpensePayment(
                        expenseId = expense.id,
                        memberId = member.id,
                        toMemberId = "",
                        amount = remaining,
                        date = store.today(),
                        note = "Payment complete"
                    ))
                }
            }
            finance.saveRoomExpensePayments(payments)
            CloudSyncManager.scheduleUpload(this)
            dialog.dismiss()
            Toast.makeText(this, "✓ ${member.name} • ${moneyText(totalDue)} PAYMENT COMPLETE", Toast.LENGTH_LONG).show()
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
    ms = replace_function(ms, '    private fun completeMemberMonthPayment(member: RoomMember) {', new_complete)

    # Reusable premium row + partial payment chooser/editor.
    helper_anchor = '    private fun completeMemberMonthPayment(member: RoomMember) {'
    require(ms, helper_anchor, 'complete member helper anchor')
    premium_helpers = r'''    private fun financePremiumActionRow(icon: String, title: String, subtitle: String, fill: String, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = premiumGradientStroke(fill, "#55FFFFFF", 1, 16)
            elevation = dp(5).toFloat()
            applyDepthPress(5)
            setOnClickListener { action() }
            addView(text(icon, 18f, "#FFFFFF", bold = true).apply {
                gravity = Gravity.CENTER
                background = premiumGradientStroke("#30FFFFFF", "#4FFFFFFF", 1, 11)
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
            labels.addView(text(title, 14.5f, "#FFFFFF", bold = true))
            labels.addView(text(subtitle, 10.5f, "#B6C1D9").apply { setPadding(0, dp(2), 0, 0) })
            addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("›", 23f, "#B6C2E1", bold = true))
        }
    }

    private fun chooseMemberPartialPayment(member: RoomMember) {
        val finance = FinanceStore(this)
        val month = LocalDate.now().toString().substring(0, 7)
        val pending = finance.roomExpenses().filter {
            it.date.startsWith(month) && member.id in it.participantIds && finance.remainingForExpense(it, member.id) > 0.005
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
        box.addView(text("${member.name} • কোন হিসাবের payment দেবেন?", 11.5f, "#9EADD0").apply { setPadding(0, dp(4), 0, dp(12)) })
        pending.forEach { expense ->
            val due = finance.remainingForExpense(expense, member.id)
            box.addView(financePremiumActionRow("৳", expense.title, "${finance.kindLabel(expense.kind)} • এখন বাকি ${moneyText(due)}", "#263A61") {
                dialog.dismiss()
                editMemberPaymentAmount(member, expense)
            })
            box.addView(space(7))
        }
        val close = Button(this).apply {
            text = "বন্ধ"; isAllCaps = false; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            background = premiumGradientStroke("#303B58", "#596A97", 1, 14)
            setOnClickListener { dialog.dismiss() }
        }
        box.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun editMemberPaymentAmount(member: RoomMember, expense: RoomExpense) {
        val finance = FinanceStore(this)
        val due = finance.remainingForExpense(expense, member.id)
        if (due < 0.005) {
            Toast.makeText(this, "✓ ${member.name} এই হিসাব ইতিমধ্যে complete", Toast.LENGTH_LONG).show()
            return
        }
        val alreadyPaid = finance.paidForExpense(expense, member.id)
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#141D38", "#7E6CFF", 1, 24)
        }
        box.addView(text("✎ Payment amount", 20f, "#FFFFFF", bold = true))
        box.addView(text(member.name, 14f, "#77D5B4", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        val summary = card("#1A2946", padding = 12)
        summary.addView(text(expense.title, 14.5f, "#FFFFFF", bold = true))
        summary.addView(text("নিজের share ${moneyText(expense.shareAmount())}", 11.5f, "#AAB8D6").apply { setPadding(0, dp(4), 0, 0) })
        summary.addView(text("আগে দিয়েছেন ${moneyText(alreadyPaid)} • এখন বাকি ${moneyText(due)}", 12f, "#F1C27D", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        box.addView(space(10)); box.addView(summary); box.addView(space(10))
        box.addView(text("এখন কত টাকা দিলেন?", 12f, "#AEBBDA", bold = true))
        val amountInput = input("Payment amount").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", due))
            selectAll()
        }
        box.addView(amountInput)
        box.addView(text("সম্পূর্ণ না দিলে amount কমিয়ে সেভ করুন। বাকি টাকা Pending থাকবে।", 10.5f, "#8292B7").apply { setPadding(0, dp(5), 0, dp(12)) })

        fun saveAmount(value: Double) {
            if (value <= 0.0 || value > due + 0.005) {
                Toast.makeText(this, "SAR 0-এর বেশি এবং সর্বোচ্চ ${moneyText(due)} পর্যন্ত দিন", Toast.LENGTH_LONG).show()
                return
            }
            val payments = finance.roomExpensePayments()
            payments.add(RoomExpensePayment(
                expenseId = expense.id,
                memberId = member.id,
                toMemberId = "",
                amount = value,
                date = store.today(),
                note = if (due - value < 0.005) "Payment complete" else "Partial payment"
            ))
            finance.saveRoomExpensePayments(payments)
            CloudSyncManager.scheduleUpload(this)
            dialog.dismiss()
            val left = (due - value).coerceAtLeast(0.0)
            Toast.makeText(this, if (left < 0.005) "✓ ${member.name} • PAYMENT COMPLETE" else "${member.name} দিয়েছেন ${moneyText(value)} • বাকি ${moneyText(left)}", Toast.LENGTH_LONG).show()
            render()
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(smallAction("Amount সেভ", "#5667D9") {
            val value = amountInput.text.toString().trim().toDoubleOrNull() ?: 0.0
            saveAmount(value)
        }, LinearLayout.LayoutParams(0, dp(49), 1f))
        actions.addView(hSpace(8))
        actions.addView(smallAction("✓ Full Complete", "#2E8A72") { saveAmount(due) }, LinearLayout.LayoutParams(0, dp(49), 1f))
        box.addView(actions)
        box.addView(space(7))
        val cancel = Button(this).apply {
            text = "বাতিল"; isAllCaps = false; setTextColor(Color.WHITE)
            background = premiumGradientStroke("#303B58", "#596A97", 1, 14)
            setOnClickListener { dialog.dismiss() }
        }
        box.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        dialog.setView(ScrollView(this).apply { addView(box) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

'''
    ms = ms.replace(helper_anchor, premium_helpers + helper_anchor, 1)

    mp.write_text(ms)
    print('v3.25 partial-payment, premium dialogs and scroll retention applied')
else:
    print('v3.25 MainActivity patch already applied')


# ---------------------------------------------------------------------------
# Premium Room/Mess PDF cards.
# ---------------------------------------------------------------------------
pp = Path('app/src/main/java/com/guide/app/PdfReportsActivity.kt')
ps = pp.read_text()
if 'GuidePdfPremiumV325' not in ps:
    new_room_pdf = r'''    // GuidePdfPremiumV325
    private fun writeRoomMess(w: PdfWriter, context: android.content.Context) {
        val finance = FinanceStore(context)
        val currency = context.getSharedPreferences("guide_ui", android.content.Context.MODE_PRIVATE).getString("currency", "SAR") ?: "SAR"
        val expenses = finance.roomExpenses()
        val allMembers = finance.roomMembers()
        val members = allMembers.filter { !it.deleted }
        val active = members.count { it.active }

        w.reportHero(
            "ROOM / MESS FINANCE",
            "Room / Mess হিসাব রিপোর্ট",
            "Active member $active জন • মোট member ${members.size} জন • Generated ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))}"
        )
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
            val monthDue = due(items)

            w.monthBanner(month, monthDue < 0.005)
            w.summaryCard("🏠 Room Rent", currency, total(rent), paid(rent), due(rent))
            w.summaryCard("🛒 Mess / বাজার", currency, total(mess), paid(mess), due(mess))
            if (other.isNotEmpty()) w.summaryCard("💡 অন্যান্য Bill", currency, total(other), paid(other), due(other))
            w.gap(7f)

            items.sortedByDescending { it.date }.forEach { expense ->
                val collection = finance.expenseCollection(expense)
                val complete = collection.third < 0.005
                val memberCount = expense.participantIds.size
                w.expenseCard(
                    "${finance.kindLabel(expense.kind)} • ${expense.title}",
                    "${expense.date}  •  Total $currency ${money(expense.amount)}  •  $memberCount জন  •  জনপ্রতি $currency ${money(expense.shareAmount())}",
                    if (complete) "PAYMENT COMPLETE" else "Paid $currency ${money(collection.second)}  •  Pending $currency ${money(collection.third)}",
                    complete
                )
                expense.participantIds.forEach { id ->
                    val member = allMembers.firstOrNull { it.id == id } ?: return@forEach
                    val memberPaid = finance.paidForExpense(expense, id)
                    val memberDue = finance.remainingForExpense(expense, id)
                    w.memberPaymentCard(
                        member.name,
                        "Share $currency ${money(expense.shareAmount())}  •  Paid $currency ${money(memberPaid)}",
                        if (memberDue < 0.005) "✓ COMPLETE" else "Pending $currency ${money(memberDue)}",
                        memberDue < 0.005
                    )
                }
                w.gap(8f)
            }
            w.sectionBreak()
        }
    }

'''
    ps = replace_function(ps, '    private fun writeRoomMess(w: PdfWriter, context: android.content.Context) {', new_room_pdf)

    writer_anchor = '        fun heading(text: String) {'
    require(ps, writer_anchor, 'PdfWriter heading anchor')
    writer_helpers = r'''        fun reportHero(kicker: String, titleText: String, subtitleText: String) {
            ensure(98f)
            val c = canvas ?: return
            val top = y
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 246, 254) }
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(103, 82, 238) }
            val kickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(103, 82, 238); textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val heroTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 31, 58); textSize = 19f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(102, 112, 142); textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            c.drawRoundRect(LEFT, top, RIGHT, top + 78f, 14f, 14f, bg)
            c.drawRoundRect(LEFT, top, LEFT + 6f, top + 78f, 3f, 3f, accent)
            c.drawText(kicker, LEFT + 18f, top + 18f, kickerPaint)
            c.drawText(fitText(titleText, heroTitle, RIGHT - LEFT - 36f), LEFT + 18f, top + 43f, heroTitle)
            c.drawText(fitText(subtitleText, meta, RIGHT - LEFT - 36f), LEFT + 18f, top + 64f, meta)
            y = top + 92f
        }

        fun monthBanner(month: String, complete: Boolean) {
            ensure(48f)
            val c = canvas ?: return
            val top = y
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(232, 248, 240) else Color.rgb(239, 242, 252) }
            val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 37, 70); textSize = 15f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(35, 139, 103) else Color.rgb(210, 128, 47); textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            c.drawRoundRect(LEFT, top, RIGHT, top + 35f, 10f, 10f, bg)
            c.drawText(month, LEFT + 14f, top + 23f, monthPaint)
            val status = if (complete) "✓ MONTH COMPLETE" else "PAYMENT IN PROGRESS"
            c.drawText(status, RIGHT - statusPaint.measureText(status) - 14f, top + 22f, statusPaint)
            y = top + 45f
        }

        fun summaryCard(labelText: String, currency: String, totalValue: Double, paidValue: Double, dueValue: Double) {
            ensure(72f)
            val c = canvas ?: return
            val top = y
            val complete = totalValue > 0.005 && dueValue < 0.005
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(239, 250, 245) else Color.rgb(248, 249, 253) }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(39, 49, 82); textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 38, 62); textSize = 14f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val paidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(41, 150, 113); textSize = 9.5f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val duePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dueValue < 0.005) Color.rgb(41, 150, 113) else Color.rgb(205, 89, 91); textSize = 9.5f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            c.drawRoundRect(LEFT, top, RIGHT, top + 60f, 12f, 12f, bg)
            c.drawText(labelText, LEFT + 14f, top + 18f, labelPaint)
            c.drawText("Total $currency ${money(totalValue)}", LEFT + 14f, top + 40f, totalPaint)
            val paidText = "Paid ${money(paidValue)}"
            val dueText = if (dueValue < 0.005 && totalValue > 0.005) "✓ COMPLETE" else "Pending ${money(dueValue)}"
            c.drawText(paidText, RIGHT - 150f, top + 22f, paidPaint)
            c.drawText(dueText, RIGHT - 150f, top + 43f, duePaint)
            y = top + 68f
        }

        fun expenseCard(titleText: String, metaText: String, statusText: String, complete: Boolean) {
            ensure(76f)
            val c = canvas ?: return
            val top = y
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 247, 252) }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 34, 60); textSize = 11.5f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 111, 140); textSize = 8.5f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(39, 146, 108) else Color.rgb(201, 92, 94); textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(76, 190, 145) else Color.rgb(232, 168, 84) }
            c.drawRoundRect(LEFT, top, RIGHT, top + 64f, 12f, 12f, bg)
            c.drawRoundRect(LEFT, top, LEFT + 5f, top + 64f, 2f, 2f, accent)
            c.drawText(fitText(titleText, titlePaint, RIGHT - LEFT - 30f), LEFT + 15f, top + 19f, titlePaint)
            c.drawText(fitText(metaText, metaPaint, RIGHT - LEFT - 30f), LEFT + 15f, top + 38f, metaPaint)
            c.drawText(fitText(statusText, statusPaint, RIGHT - LEFT - 30f), LEFT + 15f, top + 55f, statusPaint)
            y = top + 70f
        }

        fun memberPaymentCard(name: String, detailText: String, statusText: String, complete: Boolean) {
            ensure(47f)
            val c = canvas ?: return
            val top = y
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(247, 252, 250) else Color.rgb(252, 248, 248) }
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(44, 53, 78); textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(112, 121, 145); textSize = 8f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (complete) Color.rgb(42, 151, 112) else Color.rgb(204, 90, 92); textSize = 8.5f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            c.drawRoundRect(LEFT + 18f, top, RIGHT, top + 39f, 9f, 9f, bg)
            c.drawText(fitText(name, namePaint, 150f), LEFT + 29f, top + 16f, namePaint)
            c.drawText(fitText(detailText, detailPaint, 260f), LEFT + 29f, top + 31f, detailPaint)
            c.drawText(fitText(statusText, statusPaint, 120f), RIGHT - 128f, top + 22f, statusPaint)
            y = top + 44f
        }

        fun gap(height: Float) {
            ensure(height + 2f)
            y += height
        }

        private fun fitText(value: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(value) <= maxWidth) return value
            var cut = value
            while (cut.length > 3 && paint.measureText("$cut…") > maxWidth) cut = cut.dropLast(1)
            return "$cut…"
        }

'''
    ps = ps.replace(writer_anchor, writer_helpers + writer_anchor, 1)
    pp.write_text(ps)
    print('v3.25 premium Room/Mess PDF layout applied')
else:
    print('v3.25 PDF patch already applied')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 38', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.25.0"', gs, count=1)
gp.write_text(gs)
print('v3.25 version metadata applied')

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text().replace('3.24.0', '3.25.0')
    cp.write_text(cs)
    print('v3.25 cloud metadata applied')
