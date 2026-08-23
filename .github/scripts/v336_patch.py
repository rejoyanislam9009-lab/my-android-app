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


# Guide v3.36
# - Completion is reversible instead of being a destructive/final state.
# - Room/Mess paid totals can be corrected after full completion.
# - Archived History items can be reopened into the active page.
# - Corrections append signed audit entries instead of deleting old payment history.
# - Existing routine/meal completion toggles and attendance status editing remain reversible.
# - Course progress gets explicit backwards/reset controls as well.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideReversibleCompletionV336' not in ms:
    new_member_actions = r'''    // GuideReversibleCompletionV336
    private fun roomExpenseMemberActionsV328(expense: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val paid = finance.paidForExpense(expense, member.id)
        val due = finance.remainingForExpense(expense, member.id)
        val share = expense.shareAmount()
        val dialog = AlertDialog.Builder(this).create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#121C36", "#7E6BFF", 1, 24)
        }
        box.addView(text(member.name, 20f, "#FFFFFF", bold = true))
        box.addView(text("${expense.title} • ${finance.kindLabel(expense.kind)}", 11.5f, "#A2B0D0", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        box.addView(text("Share ${moneyText(share)} • Paid ${moneyText(paid)} • Pending ${moneyText(due)}", 13f, if (due < 0.005) "#73D8B3" else "#F2C37D", bold = true).apply { setPadding(0, dp(8), 0, dp(10)) })

        // Always expose paid-total editing, even after PAYMENT COMPLETE. This is
        // the key v3.36 behavior: a mistaken final state is never locked forever.
        box.addView(financePremiumActionRow("✎", "Paid amount Edit / Correction", "বর্তমান Paid ${moneyText(paid)} • সম্পূর্ণ হলেও আবার edit করা যাবে", "#2C5A79") {
            dialog.dismiss()
            editExpenseMemberPaidTotalV336(expense, member)
        })

        if (paid > 0.005) {
            box.addView(space(8))
            box.addView(financePremiumActionRow("↶", "Payment Undo / আবার Uncomplete", "Paid 0 করে Pending-এ ফিরবে • পুরোনো record audit-এ থাকবে", "#70414E") {
                dialog.dismiss()
                confirmResetExpenseMemberPaymentV336(expense, member)
            })
        }

        if (due > 0.005) {
            box.addView(space(8))
            box.addView(financePremiumActionRow("✓", "Full Payment Complete", "শুধু এই হিসাবের বাকি ${moneyText(due)} complete করুন", "#246856") {
                dialog.dismiss()
                completeExpenseMemberPaymentV328(expense, member)
            })
        } else {
            box.addView(space(8))
            val done = card("#173A31", padding = 11)
            done.addView(text("✓ PAYMENT COMPLETE", 13f, "#75DAB5", bold = true))
            done.addView(text("ভুল হলে উপরের Edit বা Undo ব্যবহার করে আবার Pending করতে পারবেন।", 10.5f, "#9FBDAF").apply { setPadding(0, dp(3), 0, 0) })
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

'''
    ms = replace_function(ms, '    private fun roomExpenseMemberActionsV328(expense: RoomExpense, member: RoomMember) {', new_member_actions)

    helper_anchor = '    private fun completeExpenseMemberPaymentV328(expense: RoomExpense, member: RoomMember) {'
    require(ms, helper_anchor, 'complete expense payment helper')
    helpers = r'''    private fun editExpenseMemberPaidTotalV336(expense: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val share = expense.shareAmount().coerceAtLeast(0.0)
        val currentPaid = finance.paidForExpense(expense, member.id).coerceAtLeast(0.0)
        val input = EditText(this).apply {
            hint = "0.00 - ${String.format(Locale.US, "%.2f", share)}"
            setText(String.format(Locale.US, "%.2f", currentPaid.coerceAtMost(share)))
            selectAll()
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8290B1"))
            textSize = 18f
            setPadding(dp(14), 0, dp(14), 0)
            background = premiumGradientStroke("#151F3A", "#6377B7", 1, 14)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = premiumGradientStroke("#111C37", "#697AFF", 1, 24)
        }
        box.addView(text("✎ Paid amount Correction", 20f, "#FFFFFF", bold = true))
        box.addView(text("${member.name} • ${expense.title}", 12f, "#9FAFD1", bold = true).apply { setPadding(0, dp(4), 0, 0) })
        box.addView(text("Share ${moneyText(share)} • এখন Paid ${moneyText(currentPaid)}", 13f, "#E7C17F", bold = true).apply { setPadding(0, dp(8), 0, dp(10)) })
        box.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        box.addView(text("এখানে মোট Paid কত হওয়া উচিত সেটি লিখুন। কমালে payment আবার Pending হবে। পুরোনো payment delete না করে correction audit entry রাখা হবে।", 10.5f, "#93A3C4").apply { setPadding(0, dp(8), 0, dp(12)) })

        val dialog = AlertDialog.Builder(this).create()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(smallAction("বাতিল", "#37425E") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(hSpace(8))
        row.addView(smallAction("✓ Correction Save", "#2B8068") {
            val target = input.text.toString().trim().toDoubleOrNull()
            if (target == null || target < -0.005 || target > share + 0.005) {
                Toast.makeText(this, "0 থেকে ${moneyText(share)}-এর মধ্যে amount লিখুন", Toast.LENGTH_LONG).show()
                return@smallAction
            }
            dialog.dismiss()
            applyExpenseMemberPaidTargetV336(expense, member, target.coerceIn(0.0, share))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(row)
        dialog.setView(box)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun confirmResetExpenseMemberPaymentV336(expense: RoomExpense, member: RoomMember) {
        val finance = FinanceStore(this)
        val currentPaid = finance.paidForExpense(expense, member.id)
        AlertDialog.Builder(this)
            .setTitle("↶ Payment Undo করবেন?")
            .setMessage("${member.name}\n${expense.title}\n\nবর্তমান Paid ${moneyText(currentPaid)} → Paid ${moneyText(0.0)} হবে।\n\nআগের record delete হবে না; correction হিসেবে History/PDF audit-এ থাকবে।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Undo → Pending") { _, _ ->
                applyExpenseMemberPaidTargetV336(expense, member, 0.0)
            }
            .show()
    }

    private fun applyExpenseMemberPaidTargetV336(expense: RoomExpense, member: RoomMember, targetPaid: Double) {
        val finance = FinanceStore(this)
        val share = expense.shareAmount().coerceAtLeast(0.0)
        val current = finance.paidForExpense(expense, member.id)
        val target = targetPaid.coerceIn(0.0, share)
        val delta = target - current
        if (kotlin.math.abs(delta) < 0.005) {
            Toast.makeText(this, "কোনো পরিবর্তন নেই", Toast.LENGTH_SHORT).show()
            return
        }

        // If a completed/cleared History expense is corrected below the share,
        // put it back into the active page before saving the signed adjustment.
        if (target < share - 0.005 && finance.isExpenseArchivedV328(expense.id)) {
            finance.restoreExpenseFromHistoryV328(expense.id)
        }

        val payments = finance.roomExpensePayments()
        payments.add(RoomExpensePayment(
            expenseId = expense.id,
            memberId = member.id,
            toMemberId = "",
            amount = delta,
            date = store.today(),
            note = if (delta < 0) "Payment correction / reopened" else "Payment correction"
        ))
        finance.saveRoomExpensePayments(payments)
        CloudSyncManager.scheduleUpload(this)

        val remaining = (share - target).coerceAtLeast(0.0)
        val message = if (remaining < 0.005) {
            "✓ ${member.name} • PAYMENT COMPLETE • Paid ${moneyText(target)}"
        } else {
            "↶ ${member.name} আবার Pending • Paid ${moneyText(target)} • বাকি ${moneyText(remaining)}"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        render()
    }

    private fun reopenExpenseFromHistoryV336(expense: RoomExpense) {
        val finance = FinanceStore(this)
        if (!finance.isExpenseArchivedV328(expense.id)) {
            Toast.makeText(this, "এই হিসাব ইতিমধ্যে Active আছে", Toast.LENGTH_SHORT).show()
            return
        }
        finance.restoreExpenseFromHistoryV328(expense.id)
        CloudSyncManager.scheduleUpload(this)
        Toast.makeText(this, "↶ ${expense.title} History থেকে Active-এ ফিরেছে • এখন member payment edit/undo করতে পারবেন", Toast.LENGTH_LONG).show()
        render()
    }

'''
    ms = ms.replace(helper_anchor, helpers + helper_anchor, 1)

    # Add Reopen to every archived expense card in the sidebar History dialog.
    history_anchor = '''                monthCard.addView(itemBox)
                monthCard.addView(space(7))'''
    require(ms, history_anchor, 'history expense card append')
    history_actions = '''                itemBox.addView(space(9))
                val reopenRowV336 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                reopenRowV336.addView(smallAction("↶ Reopen / Edit", "#6A4D79") {
                    dialog.dismiss()
                    reopenExpenseFromHistoryV336(expense)
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
                reopenRowV336.addView(hSpace(7))
                reopenRowV336.addView(smallAction("📄 এই হিসাব PDF", "#28765E") {
                    shareRoomExpensePdfV328(expense)
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
                itemBox.addView(reopenRowV336)
                itemBox.addView(text("ভুল Complete হলে Reopen করুন → Active page-এ member box থেকে Paid amount edit বা Undo করুন।", 9.8f, "#9B9FC9").apply { setPadding(0, dp(6), 0, 0) })
                monthCard.addView(itemBox)
                monthCard.addView(space(7))'''
    ms = ms.replace(history_anchor, history_actions, 1)

    # Course/progress is another final-looking state. Make backwards correction
    # explicit instead of only allowing +10. The full editor still accepts 0-100.
    course_new = r'''    private fun courseActions(item: CourseItem) {
        AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf(
            "Edit / progress 0-100",
            "+10% progress",
            "↶ -10% / Undo progress",
            "Reset progress to 0%",
            "Delete"
        )) { _, which ->
            val items = store.courses()
            val index = items.indexOfFirst { it.id == item.id }
            if (index < 0) return@setItems
            when (which) {
                0 -> { addCourse(item); return@setItems }
                1 -> items[index] = item.copy(progress = (item.progress + 10).coerceAtMost(100))
                2 -> items[index] = item.copy(progress = (item.progress - 10).coerceAtLeast(0))
                3 -> items[index] = item.copy(progress = 0)
                4 -> items.removeAt(index)
            }
            store.saveCourses(items)
            CloudSyncManager.scheduleUpload(this)
            render()
        }.show()
    }

'''
    ms = replace_function(ms, '    private fun courseActions(item: CourseItem) {', course_new)

    mp.write_text(ms)
    print('v3.36 reversible completion/payment/history controls applied')
else:
    print('v3.36 MainActivity patch already applied')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 49', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.36.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.36.0"', cs, count=1)
    cp.write_text(cs)
print('v3.36 version metadata applied')
