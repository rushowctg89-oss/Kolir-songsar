package bd.myfamily.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

class AppVm(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val dao = db.dao()
    private fun <T> Flow<T>.st(i: T) = stateIn(viewModelScope, SharingStarted.Eagerly, i)

    val txs = dao.txs().st(emptyList())
    val budget = dao.budget().st(emptyList())
    val creditors = dao.creditors().st(emptyList())
    val pays = dao.pays().st(emptyList())
    val goals = dao.goals().st(emptyList())
    val ups = dao.ups().st(emptyList())
    val kv = dao.kvs().map { l -> l.associate { it.k to it.v } }.st(emptyMap())

    init { viewModelScope.launch { seed() } }

    private suspend fun seed() {
        if (dao.getKv("seeded") == null) {
            listOf(
                BudgetItem("family", "পারিবারিক অবদান (ঋণের অংশসহ)", 11000, 1),
                BudgetItem("transport", "যাতায়াত", 5000, 2),
                BudgetItem("child", "সন্তানের খরচ", 4000, 3),
                BudgetItem("personal", "ব্যক্তিগত খরচ", 4000, 4),
                BudgetItem("insurance", "বীমা রিজার্ভ", 4333, 5),
                BudgetItem("emergency", "জরুরি সঞ্চয়", 2000, 6),
                BudgetItem("medical", "চিকিৎসা ও অনিয়মিত খরচ", 3000, 7),
                BudgetItem("extradebt", "ঐচ্ছিক বাড়তি ঋণ পরিশোধ", 2000, 8),
                BudgetItem("buffer", "নগদ বাফার", 2667, 9)
            ).forEach { dao.upB(it) }
            dao.upG(Goal(name = "জরুরি তহবিল", target = 10000, saved = 0, cat = "emergency"))
            mapOf(
                "salary" to "40000", "premium" to "52000", "debtInFamily" to "3000",
                "debtMin" to "500000", "debtMax" to "1000000", "debtConfirmed" to "0",
                "b1" to "ভাই ১", "b2" to "ভাই ২", "b3" to "ভাই ৩", "b4" to "ভাই ৪", "seeded" to "1"
            ).forEach { (k, v) -> dao.setKv(Kv(k, v)) }
        }
        val ym = YearMonth.now().toString()
        if (dao.getKv("salaryMonth") != ym) {
            val sal = dao.getKv("salary")?.toLongOrNull() ?: 40000L
            dao.upTx(Tx(type = "INCOME", date = LocalDate.now().withDayOfMonth(1).toEpochDay(), cat = "salary", amount = sal, note = "মাসিক বেতন (স্বয়ংক্রিয়)"))
            dao.setKv(Kv("salaryMonth", ym))
        }
    }

    fun name(c: String): String = when (c) {
        "salary" -> "বেতন"
        "extra_income" -> "অতিরিক্ত আয়"
        "insurance_paid" -> "বীমা প্রিমিয়াম (প্রকৃত পরিশোধ)"
        else -> budget.value.firstOrNull { it.code == c }?.name ?: "অন্যান্য"
    }

    fun saveTx(t: Tx, force: Boolean, done: (Boolean) -> Unit) = viewModelScope.launch {
        val d = if (force) 0 else dao.dup(t.type, t.date, t.cat, t.amount, t.note, t.id)
        if (d > 0) done(false) else { dao.upTx(t); done(true) }
    }
    fun delTx(t: Tx) = viewModelScope.launch { dao.delTx(t) }
    fun setKv(k: String, v: String) = viewModelScope.launch { dao.setKv(Kv(k, v)) }
    fun saveBudget(b: BudgetItem) = viewModelScope.launch { dao.upB(b) }
    fun delBudget(b: BudgetItem) = viewModelScope.launch { dao.delB(b) }
    fun saveCreditor(c: Creditor) = viewModelScope.launch { dao.upC(c) }
    fun delCreditor(c: Creditor) = viewModelScope.launch { dao.delC(c) }
    fun savePay(p: DebtPay, done: (Boolean) -> Unit) = viewModelScope.launch {
        val dup = pays.value.any { it.id != p.id && it.date == p.date && it.creditorId == p.creditorId && it.brother == p.brother && it.amount == p.amount }
        if (dup) done(false) else { dao.upP(p); done(true) }
    }
    fun delPay(p: DebtPay) = viewModelScope.launch { dao.delP(p) }
    fun saveGoal(g: Goal) = viewModelScope.launch { dao.upG(g) }
    fun delGoal(g: Goal) = viewModelScope.launch { dao.delG(g) }
    fun saveUp(u: Upcoming) = viewModelScope.launch { dao.upU(u) }
    fun delUp(u: Upcoming) = viewModelScope.launch { dao.delU(u) }

    fun csv(): String {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("\uFEFFতারিখ,ধরন,ক্যাটাগরি,পরিমাণ (টাকা),নোট\n")
        txs.value.sortedBy { it.date }.forEach {
            sb.append("${LocalDate.ofEpochDay(it.date)},${if (it.type == "INCOME") "আয়" else "খরচ"},${q(name(it.cat))},${it.amount},${q(it.note)}\n")
        }
        return sb.toString()
    }

    suspend fun backupJson(): String {
        val o = JSONObject().put("app", "MyFamilyBudgetBD").put("version", 1)
        o.put("tx", JSONArray().apply { dao.allTx().forEach { put(JSONObject().put("id", it.id).put("type", it.type).put("date", it.date).put("cat", it.cat).put("amount", it.amount).put("note", it.note)) } })
        o.put("budget", JSONArray().apply { dao.allBudget().forEach { put(JSONObject().put("code", it.code).put("name", it.name).put("amount", it.amount).put("sort", it.sort)) } })
        o.put("creditor", JSONArray().apply { dao.allCreditors().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount ?: JSONObject.NULL).put("note", it.note)) } })
        o.put("debtpay", JSONArray().apply { dao.allPays().forEach { put(JSONObject().put("id", it.id).put("date", it.date).put("creditorId", it.creditorId).put("brother", it.brother).put("amount", it.amount).put("note", it.note)) } })
        o.put("goal", JSONArray().apply { dao.allGoals().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("target", it.target).put("saved", it.saved).put("cat", it.cat ?: JSONObject.NULL)) } })
        o.put("upcoming", JSONArray().apply { dao.allUps().forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("amount", it.amount).put("due", it.due).put("done", it.done)) } })
        o.put("kv", JSONArray().apply { dao.allKv().forEach { put(JSONObject().put("k", it.k).put("v", it.v)) } })
        return o.toString()
    }

    private fun <T> JSONArray.mp(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

    suspend fun restore(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            if (o.optString("app") != "MyFamilyBudgetBD") return false
            // প্রথমে সব পার্স — ভুল ফাইল হলে বিদ্যমান ডেটা অক্ষত থাকবে
            val a = o.getJSONArray("tx").mp { Tx(it.getLong("id"), it.getString("type"), it.getLong("date"), it.getString("cat"), it.getLong("amount"), it.getString("note")) }
            val b = o.getJSONArray("budget").mp { BudgetItem(it.getString("code"), it.getString("name"), it.getLong("amount"), it.getInt("sort")) }
            val c = o.getJSONArray("creditor").mp { Creditor(it.getLong("id"), it.getString("name"), if (it.isNull("amount")) null else it.getLong("amount"), it.getString("note")) }
            val p = o.getJSONArray("debtpay").mp { DebtPay(it.getLong("id"), it.getLong("date"), it.getLong("creditorId"), it.getString("brother"), it.getLong("amount"), it.getString("note")) }
            val g = o.getJSONArray("goal").mp { Goal(it.getLong("id"), it.getString("name"), it.getLong("target"), it.getLong("saved"), if (it.isNull("cat")) null else it.getString("cat")) }
            val u = o.getJSONArray("upcoming").mp { Upcoming(it.getLong("id"), it.getString("title"), it.getLong("amount"), it.getLong("due"), it.getBoolean("done")) }
            val k = o.getJSONArray("kv").mp { Kv(it.getString("k"), it.getString("v")) }
            withContext(Dispatchers.IO) { db.clearAllTables() }
            db.withTransaction {
                a.forEach { dao.upTx(it) }; b.forEach { dao.upB(it) }; c.forEach { dao.upC(it) }
                p.forEach { dao.upP(it) }; g.forEach { dao.upG(it) }; u.forEach { dao.upU(it) }; k.forEach { dao.setKv(it) }
            }
            true
        } catch (e: Exception) { false }
    }
}
