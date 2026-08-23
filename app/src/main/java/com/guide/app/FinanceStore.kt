package com.guide.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

data class DebtRecord(
    val id: String = UUID.randomUUID().toString(),
    val person: String,
    val direction: String,
    val amount: Double,
    val paidAmount: Double = 0.0,
    val note: String = "",
    val date: String = LocalDate.now().toString()
) {
    fun remaining(): Double = (amount - paidAmount).coerceAtLeast(0.0)
    fun isSettled(): Boolean = remaining() < 0.005
}

data class RoomMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val active: Boolean = true
)

data class RoomExpense(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val paidById: String,
    val participantIds: Set<String>,
    val date: String = LocalDate.now().toString(),
    val note: String = "",
    val kind: String = FinanceStore.KIND_OTHER
) {
    fun shareAmount(): Double = if (participantIds.isEmpty()) 0.0 else amount / participantIds.size
}

data class RoomExpensePayment(
    val id: String = UUID.randomUUID().toString(),
    val expenseId: String,
    val memberId: String,
    val toMemberId: String,
    val amount: Double,
    val date: String = LocalDate.now().toString(),
    val note: String = ""
)

data class RoomSettlement(
    val id: String = UUID.randomUUID().toString(),
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Double,
    val date: String = LocalDate.now().toString(),
    val note: String = ""
)

/**
 * Finance data intentionally lives inside guide_store SharedPreferences so the
 * existing Guide Firebase snapshot automatically backs it up and restores it.
 */
class FinanceStore(context: Context) {
    companion object {
        const val RECEIVE = "RECEIVE"
        const val PAY = "PAY"

        const val KIND_RENT = "RENT"
        const val KIND_MARKET = "MARKET"
        const val KIND_ELECTRICITY = "ELECTRICITY"
        const val KIND_INTERNET = "INTERNET"
        const val KIND_GAS = "GAS"
        const val KIND_WATER = "WATER"
        const val KIND_OTHER = "OTHER"

        private const val DEBTS_KEY = "finance_debts_v321"
        private const val MEMBERS_KEY = "room_members_v321"
        private const val EXPENSES_KEY = "room_expenses_v321"
        private const val EXPENSE_PAYMENTS_KEY = "room_expense_payments_v322"
        private const val SETTLEMENTS_KEY = "room_settlements_v321"
    }

    private val prefs = context.getSharedPreferences("guide_store", Context.MODE_PRIVATE)

    fun debts(): MutableList<DebtRecord> {
        val array = jsonArray(DEBTS_KEY)
        val out = mutableListOf<DebtRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += DebtRecord(
                id = o.optString("id", UUID.randomUUID().toString()),
                person = o.optString("person", "Unknown"),
                direction = o.optString("direction", RECEIVE),
                amount = o.optDouble("amount", 0.0).coerceAtLeast(0.0),
                paidAmount = o.optDouble("paidAmount", 0.0).coerceAtLeast(0.0),
                note = o.optString("note", ""),
                date = o.optString("date", LocalDate.now().toString())
            )
        }
        return out.sortedWith(compareBy<DebtRecord> { it.isSettled() }.thenByDescending { it.date }).toMutableList()
    }

    fun saveDebts(items: List<DebtRecord>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("person", item.person)
                put("direction", item.direction)
                put("amount", item.amount)
                put("paidAmount", item.paidAmount.coerceIn(0.0, item.amount.coerceAtLeast(0.0)))
                put("note", item.note)
                put("date", item.date)
            })
        }
        save(DEBTS_KEY, array)
    }

    fun debtSummary(): Pair<Double, Double> {
        var receive = 0.0
        var pay = 0.0
        debts().filterNot { it.isSettled() }.forEach { item ->
            if (item.direction == RECEIVE) receive += item.remaining() else pay += item.remaining()
        }
        return receive to pay
    }

    fun roomMembers(): MutableList<RoomMember> {
        val array = jsonArray(MEMBERS_KEY)
        val out = mutableListOf<RoomMember>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += RoomMember(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Member"),
                active = o.optBoolean("active", true)
            )
        }
        return out.toMutableList()
    }

    fun saveRoomMembers(items: List<RoomMember>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("active", item.active)
        }) }
        save(MEMBERS_KEY, array)
    }

    fun roomExpenses(): MutableList<RoomExpense> {
        val array = jsonArray(EXPENSES_KEY)
        val out = mutableListOf<RoomExpense>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val participants = mutableSetOf<String>()
            val ids = o.optJSONArray("participantIds") ?: JSONArray()
            for (j in 0 until ids.length()) ids.optString(j).takeIf { it.isNotBlank() }?.let(participants::add)
            val title = o.optString("title", "Shared expense")
            out += RoomExpense(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = title,
                amount = o.optDouble("amount", 0.0).coerceAtLeast(0.0),
                paidById = o.optString("paidById", ""),
                participantIds = participants,
                date = o.optString("date", LocalDate.now().toString()),
                note = o.optString("note", ""),
                kind = o.optString("kind", inferKind(title)).ifBlank { inferKind(title) }
            )
        }
        return out.sortedByDescending { it.date }.toMutableList()
    }

    fun saveRoomExpenses(items: List<RoomExpense>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("amount", item.amount)
            put("paidById", item.paidById); put("participantIds", JSONArray(item.participantIds.toList()))
            put("date", item.date); put("note", item.note); put("kind", item.kind)
        }) }
        save(EXPENSES_KEY, array)
    }

    fun roomExpensePayments(): MutableList<RoomExpensePayment> {
        val array = jsonArray(EXPENSE_PAYMENTS_KEY)
        val out = mutableListOf<RoomExpensePayment>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += RoomExpensePayment(
                id = o.optString("id", UUID.randomUUID().toString()),
                expenseId = o.optString("expenseId", ""),
                memberId = o.optString("memberId", ""),
                toMemberId = o.optString("toMemberId", ""),
                amount = o.optDouble("amount", 0.0).coerceAtLeast(0.0),
                date = o.optString("date", LocalDate.now().toString()),
                note = o.optString("note", "")
            )
        }
        return out.sortedByDescending { it.date }.toMutableList()
    }

    fun saveRoomExpensePayments(items: List<RoomExpensePayment>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("expenseId", item.expenseId); put("memberId", item.memberId)
            put("toMemberId", item.toMemberId); put("amount", item.amount); put("date", item.date); put("note", item.note)
        }) }
        save(EXPENSE_PAYMENTS_KEY, array)
    }

    fun paymentsForExpense(expenseId: String): List<RoomExpensePayment> =
        roomExpensePayments().filter { it.expenseId == expenseId }

    fun paidForExpense(expense: RoomExpense, memberId: String): Double {
        if (memberId == expense.paidById && memberId in expense.participantIds) return expense.shareAmount()
        return paymentsForExpense(expense.id).filter { it.memberId == memberId }.sumOf { it.amount }
            .coerceAtMost(expense.shareAmount().coerceAtLeast(0.0))
    }

    fun remainingForExpense(expense: RoomExpense, memberId: String): Double {
        if (memberId !in expense.participantIds) return 0.0
        if (memberId == expense.paidById) return 0.0
        return (expense.shareAmount() - paidForExpense(expense, memberId)).coerceAtLeast(0.0)
    }

    fun paymentStatus(expense: RoomExpense, memberId: String): String {
        if (memberId !in expense.participantIds) return "NOT_INCLUDED"
        if (memberId == expense.paidById) return "PAID"
        val paid = paidForExpense(expense, memberId)
        val remaining = remainingForExpense(expense, memberId)
        return when {
            remaining < 0.005 -> "PAID"
            paid > 0.005 -> "PARTIAL"
            else -> "DUE"
        }
    }

    fun expenseCollection(expense: RoomExpense): Triple<Double, Double, Double> {
        val expected = expense.participantIds
            .filter { it != expense.paidById }
            .sumOf { expense.shareAmount() }
        val collected = expense.participantIds
            .filter { it != expense.paidById }
            .sumOf { paidForExpense(expense, it) }
            .coerceAtMost(expected)
        return Triple(expected, collected, (expected - collected).coerceAtLeast(0.0))
    }

    fun deleteExpensePayments(expenseId: String) {
        val items = roomExpensePayments()
        if (items.removeAll { it.expenseId == expenseId }) saveRoomExpensePayments(items)
    }

    fun roomSettlements(): MutableList<RoomSettlement> {
        val array = jsonArray(SETTLEMENTS_KEY)
        val out = mutableListOf<RoomSettlement>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += RoomSettlement(
                id = o.optString("id", UUID.randomUUID().toString()),
                fromMemberId = o.optString("fromMemberId", ""),
                toMemberId = o.optString("toMemberId", ""),
                amount = o.optDouble("amount", 0.0).coerceAtLeast(0.0),
                date = o.optString("date", LocalDate.now().toString()),
                note = o.optString("note", "")
            )
        }
        return out.sortedByDescending { it.date }.toMutableList()
    }

    fun saveRoomSettlements(items: List<RoomSettlement>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("fromMemberId", item.fromMemberId); put("toMemberId", item.toMemberId)
            put("amount", item.amount); put("date", item.date); put("note", item.note)
        }) }
        save(SETTLEMENTS_KEY, array)
    }

    /** Positive balance = member should receive money. Negative = member still owes. */
    fun roomBalances(): Map<String, Double> {
        val balances = linkedMapOf<String, Double>()
        roomMembers().forEach { balances[it.id] = 0.0 }

        roomExpenses().forEach { expense ->
            val participants = expense.participantIds.filter { it.isNotBlank() }
            if (expense.amount <= 0.0 || participants.isEmpty()) return@forEach
            balances.putIfAbsent(expense.paidById, 0.0)
            balances[expense.paidById] = (balances[expense.paidById] ?: 0.0) + expense.amount
            val share = expense.amount / participants.size
            participants.forEach { id ->
                balances.putIfAbsent(id, 0.0)
                balances[id] = (balances[id] ?: 0.0) - share
            }
        }

        roomExpensePayments().forEach { payment ->
            if (payment.amount <= 0.0 || payment.memberId == payment.toMemberId) return@forEach
            balances.putIfAbsent(payment.memberId, 0.0)
            balances.putIfAbsent(payment.toMemberId, 0.0)
            balances[payment.memberId] = (balances[payment.memberId] ?: 0.0) + payment.amount
            balances[payment.toMemberId] = (balances[payment.toMemberId] ?: 0.0) - payment.amount
        }

        roomSettlements().forEach { settlement ->
            if (settlement.amount <= 0.0 || settlement.fromMemberId == settlement.toMemberId) return@forEach
            balances.putIfAbsent(settlement.fromMemberId, 0.0)
            balances.putIfAbsent(settlement.toMemberId, 0.0)
            balances[settlement.fromMemberId] = (balances[settlement.fromMemberId] ?: 0.0) + settlement.amount
            balances[settlement.toMemberId] = (balances[settlement.toMemberId] ?: 0.0) - settlement.amount
        }

        return balances.mapValues { (_, value) -> if (abs(value) < 0.005) 0.0 else value }
    }

    fun currentMonthRoomExpense(): Double {
        val month = LocalDate.now().toString().substring(0, 7)
        return roomExpenses().filter { it.date.startsWith(month) }.sumOf { it.amount }
    }

    fun currentMonthRoomCollected(): Double {
        val month = LocalDate.now().toString().substring(0, 7)
        val ids = roomExpenses().filter { it.date.startsWith(month) }.map { it.id }.toSet()
        return roomExpensePayments().filter { it.expenseId in ids }.sumOf { it.amount }
    }

    fun memberName(id: String): String = roomMembers().firstOrNull { it.id == id }?.name ?: "Unknown"

    fun kindLabel(kind: String): String = when (kind) {
        KIND_RENT -> "রুম ভাড়া"
        KIND_MARKET -> "বাজার"
        KIND_ELECTRICITY -> "বিদ্যুৎ"
        KIND_INTERNET -> "ইন্টারনেট"
        KIND_GAS -> "গ্যাস"
        KIND_WATER -> "পানি"
        else -> "অন্যান্য"
    }

    private fun inferKind(title: String): String {
        val value = title.lowercase()
        return when {
            value.contains("ভাড়া") || value.contains("ভাড়া") || value.contains("rent") -> KIND_RENT
            value.contains("বাজার") || value.contains("market") || value.contains("grocery") -> KIND_MARKET
            value.contains("বিদ্যুৎ") || value.contains("electric") -> KIND_ELECTRICITY
            value.contains("internet") || value.contains("ইন্টারনেট") -> KIND_INTERNET
            value.contains("gas") || value.contains("গ্যাস") -> KIND_GAS
            value.contains("water") || value.contains("পানি") -> KIND_WATER
            else -> KIND_OTHER
        }
    }

    private fun jsonArray(key: String): JSONArray = runCatching {
        JSONArray(prefs.getString(key, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun save(key: String, value: JSONArray) {
        prefs.edit().putString(key, value.toString()).apply()
    }
}
