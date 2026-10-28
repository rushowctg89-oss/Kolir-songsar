package bd.myfamily.budget

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// ---------- রিপোর্ট ----------
@Composable fun Reports(vm: AppVm, say: (String) -> Unit) {
    val tx by vm.txs.collectAsState(); val bud by vm.budget.collectAsState()
    var m by remember { mutableStateOf(YearMonth.now()) }
    val mt = tx.inMonth(m); val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val s = vm.csv()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(s.toByteArray()) } }
            say("CSV ফাইল সংরক্ষিত হয়েছে")
        }
    }
    val warns = warnings(bud, mt)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("মাসিক রিপোর্ট", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN); MonthBar(m) { m = it } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("আয়", taka(mt.income()), Modifier.weight(1f), GREEN); Stat("খরচ", taka(mt.spent()), Modifier.weight(1f), RED) } }
        item { Stat("অবশিষ্ট", taka(mt.income() - mt.spent()), Modifier.fillMaxWidth()) }
        if (warns.isNotEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFF3E0))) {
            Column(Modifier.padding(12.dp)) { Text("বাজেট সতর্কতা", fontWeight = FontWeight.Bold); warns.forEach { Text(it, fontSize = 13.sp) } } } }
        item { H("ক্যাটাগরি অনুযায়ী খরচ") }
        items(bud, key = { it.code }) { b ->
            val sp = mt.sumCat(b.code)
            Card { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(b.name, Modifier.weight(1f), fontSize = 14.sp); Text("${taka(sp)} / ${taka(b.amount)}", fontSize = 13.sp) }
                LinearProgressIndicator(progress = { if (b.amount > 0) (sp.toFloat() / b.amount).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = if (sp > b.amount) RED else GREEN)
            } }
        }
        val ip = mt.sumCat("insurance_paid")
        if (ip > 0) item { Text("বীমা প্রিমিয়াম পরিশোধ (রিজার্ভ থেকে, মোট খরচে ধরা হয়নি): ${taka(ip)}", fontSize = 12.sp) }
        item { Button(onClick = { csv.launch("amar-songsar-lenden.csv") }, modifier = Modifier.fillMaxWidth()) { Text("সব লেনদেন CSV হিসেবে এক্সপোর্ট") } }
    }
}

// ---------- বীমা ----------
@Composable fun Insurance(vm: AppVm) {
    val tx by vm.txs.collectAsState(); val kv by vm.kv.collectAsState()
    val prem = kv.l("premium", 52000); val saved = tx.sumCat("insurance"); val paid = tx.sumCat("insurance_paid")
    val bal = saved - paid
    var editP by remember { mutableStateOf(false) }; var dlg by remember { mutableStateOf<String?>(null) }
    var due by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("বার্ষিক বীমা", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN)
        Stat("বার্ষিক প্রিমিয়াম", taka(prem), Modifier.fillMaxWidth(), sub = "মাসিক প্রয়োজন প্রায় ${taka((prem + 11) / 12)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("রিজার্ভে জমা (মোট)", taka(saved), Modifier.weight(1f), GREEN)
            Stat("প্রকৃত পরিশোধ", taka(paid), Modifier.weight(1f), RED)
        }
        Stat("বর্তমান রিজার্ভ ব্যালেন্স", taka(bal), Modifier.fillMaxWidth(), sub = "জমা − পরিশোধ। জমা ও পরিশোধ আলাদাভাবে হিসাব হয়।")
        LinearProgressIndicator(progress = { if (prem > 0) (bal.toFloat() / prem).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
        Text(if (bal >= prem) "প্রিমিয়ামের জন্য পর্যাপ্ত রিজার্ভ আছে ✔" else "প্রিমিয়ামের জন্য আরও ${taka(prem - bal)} দরকার", fontSize = 13.sp)
        kv["premiumDue"]?.toLongOrNull()?.let { Text("প্রিমিয়ামের তারিখ: ${dateBn(it)}") }
        Button(onClick = { dlg = "insurance" }, modifier = Modifier.fillMaxWidth()) { Text("+ রিজার্ভে টাকা জমা") }
        Button(onClick = { dlg = "insurance_paid" }, modifier = Modifier.fillMaxWidth()) { Text("প্রিমিয়াম পরিশোধ রেকর্ড করুন") }
        OutlinedButton(onClick = { due = true }, modifier = Modifier.fillMaxWidth()) { Text("প্রিমিয়ামের তারিখ ঠিক করুন") }
        OutlinedButton(onClick = { editP = true }, modifier = Modifier.fillMaxWidth()) { Text("প্রিমিয়ামের পরিমাণ পরিবর্তন") }
    }
    if (editP) NumDialog("বার্ষিক প্রিমিয়াম", prem, { vm.setKv("premium", it.toString()) }) { editP = false }
    dlg?.let { c -> TxDialog(vm, null, "EXPENSE", c) { dlg = null } }
    if (due) {
        var d by remember { mutableStateOf(kv["premiumDue"]?.toLongOrNull() ?: LocalDate.now().plusMonths(6).toEpochDay()) }
        AlertDialog(onDismissRequest = { due = false }, title = { Text("প্রিমিয়ামের তারিখ") }, text = { DateBtn(d) { d = it } },
            confirmButton = { TextButton(onClick = { vm.setKv("premiumDue", d.toString()); due = false }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { due = false }) { Text("বাতিল") } })
    }
}

// ---------- লক্ষ্য ----------
@Composable fun Goals(vm: AppVm) {
    val goals by vm.goals.collectAsState(); val tx by vm.txs.collectAsState()
    var edit by remember { mutableStateOf<Goal?>(null) }; var new by remember { mutableStateOf(false) }; var del by remember { mutableStateOf<Goal?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("সঞ্চয় লক্ষ্য", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN) }
        items(goals, key = { it.id }) { g ->
            val saved = if (g.cat != null) tx.sumCat(g.cat) else g.saved
            Card { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { edit = g }) { Icon(Icons.Default.Edit, "সম্পাদনা") }
                    IconButton(onClick = { del = g }) { Icon(Icons.Default.Delete, "মুছুন") }
                }
                Text("${taka(saved)} / ${taka(g.target)}" + if (g.cat != null) "  (জরুরি সঞ্চয় লেনদেন থেকে স্বয়ংক্রিয়)" else "", fontSize = 13.sp)
                LinearProgressIndicator(progress = { if (g.target > 0) (saved.toFloat() / g.target).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (saved >= g.target) Text("লক্ষ্য পূরণ হয়েছে 🎉", color = GREEN, fontSize = 13.sp)
            } }
        }
        item { Button(onClick = { new = true }, modifier = Modifier.fillMaxWidth()) { Text("+ নতুন লক্ষ্য") } }
    }
    if (edit != null || new) {
        val g = edit
        var n by remember { mutableStateOf(g?.name ?: "") }; var t by remember { mutableStateOf(g?.target?.toString() ?: "") }
        var s by remember { mutableStateOf(g?.saved?.toString() ?: "0") }; var err by remember { mutableStateOf<String?>(null) }
        val close = { edit = null; new = false }
        AlertDialog(onDismissRequest = close, title = { Text("লক্ষ্য") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("লক্ষ্যের নাম") }, singleLine = true)
                AmtField(t, "লক্ষ্যমাত্রা (৳)") { t = it }
                if (g?.cat == null) AmtField(s, "এ পর্যন্ত জমা (৳)") { s = it }
                err?.let { Text(it, color = RED, fontSize = 12.sp) } } },
            confirmButton = { TextButton(onClick = {
                val tv = parseAmount(t)
                if (n.isBlank()) err = "নাম লিখুন" else if (tv == null || tv <= 0) err = "সঠিক লক্ষ্যমাত্রা লিখুন"
                else { vm.saveGoal(Goal(g?.id ?: 0, n.trim(), tv, parseAmount(s) ?: 0, g?.cat)); close() }
            }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = close) { Text("বাতিল") } })
    }
    del?.let { g -> Confirm("‘${g.name}’ লক্ষ্য মুছবেন?", "মুছুন", { vm.delGoal(g); del = null }, { del = null }) }
}

// ---------- ব্যাকআপ ----------
@Composable fun Backup(vm: AppVm, say: (String) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    val bk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val s = vm.backupJson()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(s.toByteArray()) } }
            say("ব্যাকআপ সংরক্ষিত হয়েছে")
        }
    }
    val rs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> pending = uri }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ব্যাকআপ ও রিস্টোর", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN)
        Text("আপনার সব তথ্য ফোনেই সংরক্ষিত থাকে, অ্যাপ বন্ধ বা আপডেট করলেও মুছে যায় না। ফোন বদলানো বা অ্যাপ আনইনস্টলের আগে অবশ্যই ব্যাকআপ রাখুন।", fontSize = 14.sp)
        Button(onClick = { bk.launch("amar-songsar-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("ব্যাকআপ ফাইল তৈরি করুন") }
        OutlinedButton(onClick = { rs.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("ব্যাকআপ থেকে রিস্টোর করুন") }
        Text("সতর্কতা: রিস্টোর করলে বর্তমান সব তথ্য ব্যাকআপের তথ্য দিয়ে বদলে যাবে।", fontSize = 12.sp, color = RED)
    }
    pending?.let { uri ->
        Confirm("বর্তমান সব তথ্য মুছে ব্যাকআপের তথ্য বসানো হবে। চালিয়ে যাবেন?", "রিস্টোর", {
            pending = null
            scope.launch {
                val text = withContext(Dispatchers.IO) { try { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() } catch (e: Exception) { null } }
                say(if (text != null && vm.restore(text)) "রিস্টোর সম্পন্ন হয়েছে" else "ফাইলটি সঠিক ব্যাকআপ নয়, কোনো তথ্য বদলায়নি")
            }
        }, { pending = null })
    }
}
