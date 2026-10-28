package bd.myfamily.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

val GREEN = Color(0xFF006A4E)
val RED = Color(0xFFC62828)
val AMBER = Color(0xFFB26A00)

// ---------- ছোট কম্পোনেন্ট ----------
@Composable fun Stat(t: String, v: String, m: Modifier = Modifier, c: Color = Color.Unspecified, sub: String? = null) {
    Card(m) { Column(Modifier.padding(12.dp)) {
        Text(t, fontSize = 13.sp)
        Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c)
        if (sub != null) Text(sub, fontSize = 11.sp)
    } }
}

@Composable fun H(t: String) = Text(t, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GREEN, modifier = Modifier.padding(top = 6.dp))

@Composable fun AmtField(v: String, label: String, onC: (String) -> Unit) = OutlinedTextField(
    value = v,
    onValueChange = { s -> onC(s.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("").filter { ch -> ch in '0'..'9' }.take(9)) },
    label = { Text(label) }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
)

@Composable fun Pick(label: String, opts: List<Pair<String, String>>, sel: String, onSel: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(opts.firstOrNull { it.first == sel }?.second ?: "—") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            opts.forEach { o -> DropdownMenuItem(text = { Text(o.second) }, onClick = { onSel(o.first); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateBtn(day: Long, onPick: (Long) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) { Text("তারিখ: " + dateBn(day)) }
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = day * 86400000L)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86400000L)) }; show = false }) { Text("ঠিক আছে") } },
            dismissButton = { TextButton(onClick = { show = false }) { Text("বাতিল") } }
        ) { DatePicker(state = st) }
    }
}

@Composable fun Confirm(msg: String, yes: String = "হ্যাঁ", onYes: () -> Unit, onNo: () -> Unit) = AlertDialog(
    onDismissRequest = onNo, text = { Text(msg) },
    confirmButton = { TextButton(onClick = onYes) { Text(yes) } },
    dismissButton = { TextButton(onClick = onNo) { Text("না") } }
)

@Composable fun MonthBar(m: YearMonth, onC: (YearMonth) -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = { onC(m.minusMonths(1)) }) { Text("‹ আগের", fontSize = 15.sp) }
    Text(monthBn(m), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    TextButton(onClick = { onC(m.plusMonths(1)) }) { Text("পরের ›", fontSize = 15.sp) }
}

@Composable fun NumDialog(title: String, init: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(init.toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column { AmtField(a, "পরিমাণ (৳)") { a = it }; err?.let { Text(it, color = RED, fontSize = 12.sp) } } },
        confirmButton = { TextButton(onClick = {
            val v = parseAmount(a)
            if (v == null || v <= 0) err = "সঠিক পরিমাণ লিখুন" else { onSave(v); onDismiss() }
        }) { Text("সংরক্ষণ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } })
}

// ---------- মূল কাঠামো ----------
@Composable fun Root(vm: AppVm) {
    var tab by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<Int?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { m -> scope.launch { snack.showSnackbar(m) } }
    BackHandler(enabled = sub != null) { sub = null }
    val labels = listOf("হোম", "লেনদেন", "বাজেট", "ঋণ", "আরও")
    val icons = listOf(Icons.Default.Home, Icons.Default.ShoppingCart, Icons.Default.DateRange, Icons.Default.Person, Icons.Default.Menu)
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { NavigationBar { labels.forEachIndexed { i, l ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i; sub = null }, icon = { Icon(icons[i], l) }, label = { Text(l, fontSize = 11.sp) })
        } } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 4 && sub != null) {
                Column {
                    TextButton(onClick = { sub = null }) { Text("← ফিরে যান") }
                    when (sub) { 1 -> Reports(vm, say); 2 -> Insurance(vm); 3 -> Goals(vm); else -> Backup(vm, say) }
                }
            } else when (tab) {
                0 -> Dashboard(vm)
                1 -> TxScreen(vm)
                2 -> BudgetScreen(vm)
                3 -> DebtScreen(vm)
                else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("আরও", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    listOf(1 to "মাসিক রিপোর্ট ও CSV", 2 to "বার্ষিক বীমা", 3 to "সঞ্চয় লক্ষ্য", 4 to "ব্যাকআপ ও রিস্টোর").forEach { (k, t) ->
                        Button(onClick = { sub = k }, modifier = Modifier.fillMaxWidth()) { Text(t, fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}


// ---------- ড্যাশবোর্ড ----------
@Composable fun Dashboard(vm: AppVm) {
    val tx by vm.txs.collectAsState(); val kv by vm.kv.collectAsState()
    val bud by vm.budget.collectAsState(); val ups by vm.ups.collectAsState()
    val m = YearMonth.now(); val mt = tx.inMonth(m)
    val inc = mt.income(); val sp = mt.spent()
    val reserve = tx.sumCat("insurance") - tx.sumCat("insurance_paid")
    val savings = tx.sumCat("emergency")
    val warns = warnings(bud, mt)
    var addUp by remember { mutableStateOf(false) }
    val due = kv["premiumDue"]?.toLongOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("আমার সংসার", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GREEN); Text(monthBn(m)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("মাসিক আয়", taka(inc), Modifier.weight(1f), GREEN)
            Stat("প্রকৃত খরচ", taka(sp), Modifier.weight(1f), RED)
        } }
        item { Stat("অবশিষ্ট ব্যালেন্স", taka(inc - sp), Modifier.fillMaxWidth(), if (inc - sp >= 0) GREEN else RED) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("মোট সঞ্চয়", taka(savings), Modifier.weight(1f))
            Stat("বীমা রিজার্ভ", taka(reserve), Modifier.weight(1f), sub = "প্রিমিয়াম ${taka(kv.l("premium", 52000))}")
        } }
        item { Stat("পারিবারিক ঋণ", "সঠিক পরিমাণ অনিশ্চিত", Modifier.fillMaxWidth(), AMBER,
            "আনুমানিক ${taka(kv.l("debtMin"))} – ${taka(kv.l("debtMax"))}। মাসিক ${taka(kv.l("debtInFamily"))} পারিবারিক অবদানের ভেতরেই ধরা (দুইবার গণনা হয় না)") }
        if (warns.isNotEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
            Column(Modifier.padding(12.dp)) { Text("বাজেট সতর্কতা", fontWeight = FontWeight.Bold); warns.forEach { Text(it, fontSize = 13.sp) } } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            H("আসন্ন পরিশোধ"); TextButton(onClick = { addUp = true }) { Text("+ যোগ করুন") } } }
        if (due != null) item { Card { Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("বার্ষিক বীমা প্রিমিয়াম • ${dateBn(due)}"); Text(taka(kv.l("premium", 52000)), fontWeight = FontWeight.Bold) } } }
        val open = ups.filter { !it.done }
        if (open.isEmpty() && due == null) item { Text("কোনো আসন্ন পরিশোধ নেই", fontSize = 13.sp) }
        items(open, key = { it.id }) { u ->
            Card { Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(u.title, fontWeight = FontWeight.Bold); Text(dateBn(u.due) + " • " + taka(u.amount), fontSize = 12.sp) }
                IconButton(onClick = { vm.saveUp(u.copy(done = true)) }) { Icon(Icons.Default.Check, "পরিশোধিত") }
                IconButton(onClick = { vm.delUp(u) }) { Icon(Icons.Default.Delete, "মুছুন") }
            } }
        }
    }
    if (addUp) {
        var t by remember { mutableStateOf("") }; var a by remember { mutableStateOf("") }
        var d by remember { mutableStateOf(LocalDate.now().toEpochDay()) }; var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { addUp = false }, title = { Text("আসন্ন পরিশোধ") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("বিবরণ") }, singleLine = true)
                AmtField(a, "পরিমাণ (৳)") { a = it }; DateBtn(d) { d = it }
                err?.let { Text(it, color = RED, fontSize = 12.sp) } } },
            confirmButton = { TextButton(onClick = {
                val v = parseAmount(a)
                if (t.isBlank()) err = "বিবরণ লিখুন" else if (v == null || v <= 0) err = "সঠিক পরিমাণ লিখুন"
                else { vm.saveUp(Upcoming(title = t.trim(), amount = v, due = d)); addUp = false }
            }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { addUp = false }) { Text("বাতিল") } })
    }
}

// ---------- লেনদেন ----------
@Composable fun TxDialog(vm: AppVm, init: Tx?, presetType: String = "EXPENSE", presetCat: String? = null, onDone: () -> Unit) {
    val bud by vm.budget.collectAsState()
    var type by remember { mutableStateOf(init?.type ?: presetType) }
    var cat by remember { mutableStateOf(init?.cat ?: presetCat ?: "") }
    var day by remember { mutableStateOf(init?.date ?: LocalDate.now().toEpochDay()) }
    var amt by remember { mutableStateOf(init?.amount?.toString() ?: "") }
    var note by remember { mutableStateOf(init?.note ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    var force by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val cats: List<Pair<String, String>> = if (type == "INCOME") listOf("salary" to "বেতন", "extra_income" to "অতিরিক্ত আয়")
        else bud.map { it.code to it.name } + ("insurance_paid" to "বীমা প্রিমিয়াম (প্রকৃত পরিশোধ)")
    val catEff = if (cats.any { it.first == cat }) cat else cats.first().first
    AlertDialog(onDismissRequest = onDone, title = { Text(if (init == null) "নতুন লেনদেন" else "লেনদেন সম্পাদনা") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("খরচ") })
                FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("আয়") })
            }
            DateBtn(day) { day = it }
            Pick("ক্যাটাগরি", cats, catEff) { cat = it }
            AmtField(amt, "পরিমাণ (৳)") { amt = it; force = false }
            OutlinedTextField(note, { note = it }, label = { Text("নোট (ঐচ্ছিক)") })
            err?.let { Text(it, color = RED, fontSize = 12.sp) }
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            val a = parseAmount(amt)
            if (a == null || a <= 0) err = "সঠিক টাকার পরিমাণ লিখুন (০ এর বেশি)"
            else if (a > 10_000_000) err = "পরিমাণ অতিরিক্ত বেশি, আবার দেখুন"
            else {
                saving = true
                vm.saveTx(Tx(init?.id ?: 0, type, day, catEff, a, note.trim()), force) { ok ->
                    saving = false
                    if (ok) onDone() else { force = true; err = "একই তারিখ, ক্যাটাগরি ও পরিমাণের লেনদেন আগেই আছে। তবুও যোগ করতে আবার ‘সংরক্ষণ’ চাপুন।" }
                }
            }
        }) { Text("সংরক্ষণ") } },
        dismissButton = { TextButton(onClick = onDone) { Text("বাতিল") } })
}

@Composable fun TxScreen(vm: AppVm) {
    val tx by vm.txs.collectAsState(); val bud by vm.budget.collectAsState()
    var m by remember { mutableStateOf(YearMonth.now()) }
    var edit by remember { mutableStateOf<Tx?>(null) }
    var show by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Tx?>(null) }
    val list = tx.inMonth(m)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            MonthBar(m) { m = it }
            if (list.isEmpty()) Text("এই মাসে কোনো লেনদেন নেই। নিচের + বাটনে চাপুন।", Modifier.padding(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(list, key = { it.id }) { t ->
                    Card { Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(vm.name(t.cat), fontWeight = FontWeight.Bold)
                            Text(dateBn(t.date) + if (t.note.isNotBlank()) " • " + t.note else "", fontSize = 12.sp)
                        }
                        Text((if (t.type == "INCOME") "+" else "-") + taka(t.amount), color = if (t.type == "INCOME") GREEN else RED, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { edit = t; show = true }) { Icon(Icons.Default.Edit, "সম্পাদনা") }
                        IconButton(onClick = { del = t }) { Icon(Icons.Default.Delete, "মুছুন") }
                    } }
                }
            }
        }
        FloatingActionButton(onClick = { edit = null; show = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "যোগ") }
    }
    if (show) TxDialog(vm, edit) { show = false }
    del?.let { t -> Confirm("এই লেনদেন (${vm.name(t.cat)} ${taka(t.amount)}) মুছে ফেলবেন?", "মুছুন", { vm.delTx(t); del = null }, { del = null }) }
}

// ---------- বাজেট ----------
@Composable fun BudgetScreen(vm: AppVm) {
    val bud by vm.budget.collectAsState(); val kv by vm.kv.collectAsState()
    val salary = kv.l("salary", 40000); val total = bud.sumOf { it.amount }
    var editB by remember { mutableStateOf<BudgetItem?>(null) }
    var newB by remember { mutableStateOf(false) }
    var editSal by remember { mutableStateOf(false) }
    var delB by remember { mutableStateOf<BudgetItem?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("মাসিক বাজেট", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN) }
        item { Card { Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("মাসিক বেতন"); Text(taka(salary), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { editSal = true }) { Text("পরিবর্তন") } } } }
        item { Text(
            if (total == salary) "মোট বাজেট ${taka(total)} = বেতন ✔"
            else if (total > salary) "⚠ মোট বাজেট ${taka(total)}, বেতনের চেয়ে ${taka(total - salary)} বেশি"
            else "মোট বাজেট ${taka(total)}, ${taka(salary - total)} বরাদ্দহীন",
            color = if (total == salary) GREEN else AMBER, fontWeight = FontWeight.Bold) }
        item { Text("টীকা: ঋণের মাসিক ${taka(kv.l("debtInFamily"))} পারিবারিক অবদানের ভেতরেই আছে; আলাদা যোগ করা হয়নি।", fontSize = 12.sp) }
        items(bud, key = { it.code }) { b ->
            Card { Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, Modifier.weight(1f).padding(vertical = 10.dp)); Text(taka(b.amount), fontWeight = FontWeight.Bold)
                IconButton(onClick = { editB = b }) { Icon(Icons.Default.Edit, "সম্পাদনা") }
                if (b.code.startsWith("x_")) IconButton(onClick = { delB = b }) { Icon(Icons.Default.Delete, "মুছুন") }
            } }
        }
        item { Button(onClick = { newB = true }, modifier = Modifier.fillMaxWidth()) { Text("+ নতুন খাত যোগ করুন") } }
    }
    if (editSal) NumDialog("মাসিক বেতন", salary, { vm.setKv("salary", it.toString()) }) { editSal = false }
    if (editB != null || newB) {
        val b = editB
        var n by remember { mutableStateOf(b?.name ?: "") }; var a by remember { mutableStateOf(b?.amount?.toString() ?: "") }
        var err by remember { mutableStateOf<String?>(null) }
        val close = { editB = null; newB = false }
        AlertDialog(onDismissRequest = close, title = { Text(if (b == null) "নতুন খাত" else "খাত সম্পাদনা") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("খাতের নাম") }, singleLine = true)
                AmtField(a, "মাসিক বাজেট (৳)") { a = it }; err?.let { Text(it, color = RED, fontSize = 12.sp) } } },
            confirmButton = { TextButton(onClick = {
                val v = parseAmount(a)
                if (n.isBlank()) err = "নাম লিখুন" else if (v == null) err = "সঠিক পরিমাণ লিখুন"
                else { vm.saveBudget(BudgetItem(b?.code ?: "x_" + System.currentTimeMillis(), n.trim(), v, b?.sort ?: 100)); close() }
            }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = close) { Text("বাতিল") } })
    }
    delB?.let { b -> Confirm("‘${b.name}’ খাত মুছবেন? (পুরনো লেনদেন থাকবে)", "মুছুন", { vm.delBudget(b); delB = null }, { delB = null }) }
}

// ---------- ঋণ ----------
@Composable fun DebtScreen(vm: AppVm) {
    val kv by vm.kv.collectAsState(); val cr by vm.creditors.collectAsState(); val pays by vm.pays.collectAsState()
    var editC by remember { mutableStateOf<Creditor?>(null) }; var newC by remember { mutableStateOf(false) }
    var payDlg by remember { mutableStateOf(false) }; var delC by remember { mutableStateOf<Creditor?>(null) }
    var delP by remember { mutableStateOf<DebtPay?>(null) }; var exact by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<String?>(null) }
    val confirmed = kv["debtConfirmed"] == "1"
    val bros = listOf("b1", "b2", "b3", "b4")
    val bn = { c: String -> kv[c] ?: c }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("পারিবারিক ঋণ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN) }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) { Column(Modifier.padding(12.dp)) {
            if (confirmed) { Text("নিশ্চিত বকেয়া: ${taka(kv.l("debtExact"))}", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            else {
                Text("সঠিক বকেয়া: অনিশ্চিত (যাচাই হয়নি)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AMBER)
                Text("আনুমানিক মোট উত্তরাধিকার ঋণ: ${taka(kv.l("debtMin"))} থেকে ${taka(kv.l("debtMax"))}", fontSize = 13.sp)
            }
            Text("আপনার ব্যক্তিগত অংশ ধরা হয়নি।", fontSize = 12.sp)
            Row { TextButton(onClick = { exact = true }) { Text("সঠিক পরিমাণ নিশ্চিত করুন") }
                if (confirmed) TextButton(onClick = { vm.setKv("debtConfirmed", "0") }) { Text("আবার অনিশ্চিত করুন") } }
        } } }
        item { Text("মাসিক ঋণ অবদান ${taka(kv.l("debtInFamily"))} — এটি ৳১১,০০০ পারিবারিক অবদানের ভেতরেই ধরা, আলাদা খরচ হিসেবে যোগ হয় না। এখানের পেমেন্ট রেকর্ড শুধু হিসাবের জন্য; খরচের তালিকায় আলাদা লিখতে হবে না।", fontSize = 12.sp) }
        item { H("চার ভাইয়ের অবদান (রেকর্ডকৃত)") }
        items(bros) { b -> Card { Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bn(b), Modifier.weight(1f)); Text(taka(pays.filter { it.brother == b }.sumOf { it.amount }), fontWeight = FontWeight.Bold)
            IconButton(onClick = { rename = b }) { Icon(Icons.Default.Edit, "নাম বদল") } } } }
        item { Text("মোট রেকর্ডকৃত পরিশোধ: ${taka(pays.sumOf { it.amount })}", fontWeight = FontWeight.Bold) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            H("পাওনাদার"); TextButton(onClick = { newC = true }) { Text("+ যোগ করুন") } } }
        if (cr.isEmpty()) item { Text("কোনো পাওনাদার যোগ করা হয়নি", fontSize = 13.sp) }
        items(cr, key = { it.id }) { c -> Card { Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(c.name, fontWeight = FontWeight.Bold)
                Text((if (c.amount == null) "বকেয়া: অনিশ্চিত" else "বকেয়া: " + taka(c.amount)) + " • পরিশোধ: " + taka(pays.filter { it.creditorId == c.id }.sumOf { it.amount }), fontSize = 12.sp)
                if (c.note.isNotBlank()) Text(c.note, fontSize = 12.sp)
            }
            IconButton(onClick = { editC = c }) { Icon(Icons.Default.Edit, "সম্পাদনা") }
            IconButton(onClick = { delC = c }) { Icon(Icons.Default.Delete, "মুছুন") }
        } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            H("পরিশোধের রেকর্ড"); TextButton(onClick = { payDlg = true }) { Text("+ যোগ করুন") } } }
        items(pays, key = { "p" + it.id }) { p -> Card { Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(taka(p.amount) + " • " + bn(p.brother), fontWeight = FontWeight.Bold)
                Text(dateBn(p.date) + " • " + (cr.firstOrNull { it.id == p.creditorId }?.name ?: "নির্দিষ্ট পাওনাদার নয়") + if (p.note.isNotBlank()) " • " + p.note else "", fontSize = 12.sp)
            }
            IconButton(onClick = { delP = p }) { Icon(Icons.Default.Delete, "মুছুন") }
        } } }
    }
    if (exact) NumDialog("সঠিক মোট বকেয়া", kv.l("debtExact", 0).coerceAtLeast(1), { vm.setKv("debtExact", it.toString()); vm.setKv("debtConfirmed", "1") }) { exact = false }
    rename?.let { code ->
        var n by remember { mutableStateOf(bn(code)) }
        AlertDialog(onDismissRequest = { rename = null }, title = { Text("ভাইয়ের নাম") },
            text = { OutlinedTextField(n, { n = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (n.isNotBlank()) vm.setKv(code, n.trim()); rename = null }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("বাতিল") } })
    }
    if (editC != null || newC) {
        val c = editC
        var n by remember { mutableStateOf(c?.name ?: "") }; var a by remember { mutableStateOf(c?.amount?.toString() ?: "") }
        var note by remember { mutableStateOf(c?.note ?: "") }; var err by remember { mutableStateOf<String?>(null) }
        val close = { editC = null; newC = false }
        AlertDialog(onDismissRequest = close, title = { Text("পাওনাদার") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("নাম") }, singleLine = true)
                AmtField(a, "বকেয়া (না জানলে খালি রাখুন)") { a = it }
                OutlinedTextField(note, { note = it }, label = { Text("নোট") }); err?.let { Text(it, color = RED, fontSize = 12.sp) } } },
            confirmButton = { TextButton(onClick = {
                if (n.isBlank()) err = "নাম লিখুন"
                else { vm.saveCreditor(Creditor(c?.id ?: 0, n.trim(), if (a.isBlank()) null else parseAmount(a), note.trim())); close() }
            }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = close) { Text("বাতিল") } })
    }
    if (payDlg) {
        var d by remember { mutableStateOf(LocalDate.now().toEpochDay()) }; var cid by remember { mutableStateOf("0") }
        var br by remember { mutableStateOf("b1") }; var a by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { payDlg = false }, title = { Text("ঋণ পরিশোধ রেকর্ড") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateBtn(d) { d = it }
                Pick("পাওনাদার", listOf("0" to "নির্দিষ্ট নয়") + cr.map { it.id.toString() to it.name }, cid) { cid = it }
                Pick("কে দিয়েছেন", bros.map { it to bn(it) }, br) { br = it }
                AmtField(a, "পরিমাণ (৳)") { a = it }
                OutlinedTextField(note, { note = it }, label = { Text("নোট") }); err?.let { Text(it, color = RED, fontSize = 12.sp) } } },
            confirmButton = { TextButton(onClick = {
                val v = parseAmount(a)
                if (v == null || v <= 0) err = "সঠিক পরিমাণ লিখুন"
                else vm.savePay(DebtPay(date = d, creditorId = cid.toLong(), brother = br, amount = v, note = note.trim())) { ok ->
                    if (ok) payDlg = false else err = "একই রেকর্ড আগেই আছে"
                }
            }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { payDlg = false }) { Text("বাতিল") } })
    }
    delC?.let { c -> Confirm("‘${c.name}’ মুছবেন?", "মুছুন", { vm.delCreditor(c); delC = null }, { delC = null }) }
    delP?.let { p -> Confirm("এই পরিশোধ রেকর্ড মুছবেন?", "মুছুন", { vm.delPay(p); delP = null }, { delP = null }) }
}
