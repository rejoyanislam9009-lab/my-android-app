package com.guide.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

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
        private const val DEBTS_KEY = "finance_debts_v321"
        private const val MEMBERS_KEY = "room_members_v321"
        private const val EXPENSES_KEY = "room_expenses_v321"
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
            out += RoomExpense(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Shared expense"),
                amount = o.optDouble("amount", 0.0).coerceAtLeast(0.0),
                paidById = o.optString("paidById", ""),
                participantIds = participants,
                date = o.optString("date", LocalDate.now().toString()),
                note = o.optString("note", "")
            )
        }
        return out.sortedByDescending { it.date }.toMutableList()
    }

    fun saveRoomExpenses(items: List<RoomExpense>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("amount", item.amount)
            put("paidById", item.paidById); put("participantIds", JSONArray(item.participantIds.toList()))
            put("date", item.date); put("note", item.note)
        }) }
        save(EXPENSES_KEY, array)
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

    /**
     * Positive balance = member should receive money.
     * Negative balance = member still needs to pay money.
     */
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

        roomSettlements().forEach { settlement ->
            if (settlement.amount <= 0.0 || settlement.fromMemberId == settlement.toMemberId) return@forEach
            balances.putIfAbsent(settlement.fromMemberId, 0.0)
            balances.putIfAbsent(settlement.toMemberId, 0.0)
            balances[settlement.fromMemberId] = (balances[settlement.fromMemberId] ?: 0.0) + settlement.amount
            balances[settlement.toMemberId] = (balances[settlement.toMemberId] ?: 0.0) - settlement.amount
        }

        return balances.mapValues { (_, value) -> if (kotlin.math.abs(value) < 0.005) 0.0 else value }
    }

    fun currentMonthRoomExpense(): Double {
        val month = LocalDate.now().toString().substring(0, 7)
        return roomExpenses().filter { it.date.startsWith(month) }.sumOf { it.amount }
    }

    fun memberName(id: String): String = roomMembers().firstOrNull { it.id == id }?.name ?: "Unknown"

    private fun jsonArray(key: String): JSONArray = runCatching {
        JSONArray(prefs.getString(key, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun save(key: String, value: JSONArray) {
        prefs.edit().putString(key, value.toString()).apply()
    }
}
