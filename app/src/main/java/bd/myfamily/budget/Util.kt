package bd.myfamily.budget

import java.time.LocalDate
import java.time.YearMonth

val MONTHS = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")

fun bnDigits(s: String) = s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

fun taka(n: Long): String {
    var s = Math.abs(n).toString()
    if (s.length > 3) {
        val last = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = mutableListOf<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        s = parts.joinToString(",") + "," + last
    }
    return (if (n < 0) "-" else "") + "৳" + bnDigits(s)
}

fun parseAmount(s: String): Long? {
    val d = s.map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("").filter { it in '0'..'9' }
    if (d.isEmpty() || d.length > 9) return null
    return d.toLong()
}

fun dateBn(day: Long): String { val d = LocalDate.ofEpochDay(day); return bnDigits("${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}") }
fun monthBn(m: YearMonth) = bnDigits("${MONTHS[m.monthValue - 1]} ${m.year}")
fun ymOf(d: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(d))

fun List<Tx>.inMonth(m: YearMonth) = filter { ymOf(it.date) == m }
fun List<Tx>.income() = filter { it.type == "INCOME" }.sumOf { it.amount }
fun List<Tx>.spent() = filter { it.type == "EXPENSE" && it.cat != "insurance_paid" }.sumOf { it.amount }
fun List<Tx>.sumCat(c: String) = filter { it.type == "EXPENSE" && it.cat == c }.sumOf { it.amount }
fun Map<String, String>.l(k: String, d: Long = 0L) = this[k]?.toLongOrNull() ?: d

fun warnings(bud: List<BudgetItem>, mt: List<Tx>): List<String> {
    val out = mutableListOf<String>()
    val soft = setOf("transport", "child", "personal", "medical")
    for (b in bud) {
        if (b.amount <= 0) continue
        val sp = mt.sumCat(b.code)
        if (sp > b.amount) out.add("⚠ ${b.name}: বাজেট ছাড়িয়ে গেছে (${taka(sp)} / ${taka(b.amount)})")
        else if ((b.code in soft || b.code.startsWith("x_")) && sp * 100 >= b.amount * 80)
            out.add("সতর্কতা: ${b.name} বাজেটের ৮০% এর বেশি খরচ হয়েছে")
    }
    val inc = mt.income()
    if (inc > 0 && mt.spent() > inc) out.add("⚠ এই মাসের মোট খরচ আয়ের চেয়ে বেশি!")
    return out
}
