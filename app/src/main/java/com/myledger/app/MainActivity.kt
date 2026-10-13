package com.myledger.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Entry(
    val id: Long,
    val date: String,
    val person: String,
    val note: String,
    val amount: Double,
    val kind: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LedgerScreen() } }
    }
}

@Composable
fun LedgerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("my_ledger", Context.MODE_PRIVATE) }
    var entries by remember { mutableStateOf(loadEntries(prefs)) }
    var tab by remember { mutableStateOf("Overview") }
    var person by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Income") }
    var date by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) }
    var message by remember { mutableStateOf("") }

    val income = entries.filter { it.kind == "Income" }.sumOf { it.amount }
    val expense = entries.filter { it.kind == "Expense" }.sumOf { it.amount }
    val owedToMe = entries.filter { it.kind == "Owed to me" }.sumOf { it.amount }
    val iOwe = entries.filter { it.kind == "I owe" }.sumOf { it.amount }

    fun save(newEntries: List<Entry>) {
        entries = newEntries
        val array = JSONArray()
        newEntries.forEach { e ->
            array.put(JSONObject().put("id", e.id).put("date", e.date).put("person", e.person)
                .put("note", e.note).put("amount", e.amount).put("kind", e.kind))
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    Scaffold(
        topBar = {
            TopAppBarCompat(title = "My Ledger")
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Add entry", "Records").forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                "Overview" -> {
                    Text("Your money at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    SummaryCard("Income", income)
                    SummaryCard("Expenses", expense)
                    SummaryCard("People owe me", owedToMe)
                    SummaryCard("I owe others", iOwe)
                    Text("Balance from income and expenses: " + money(income - expense), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text("Records are saved on this device. Export a backup from Records.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                "Add entry" -> {
                    Text("Add a record", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Type")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Income", "Expense", "Owed to me", "I owe").forEach {
                            FilterChip(selected = kind == it, onClick = { kind = it }, label = { Text(it) })
                        }
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (date.isBlank() || amount == null || amount <= 0 || note.isBlank()) {
                            message = "Enter a valid date, description and amount."
                        } else {
                            save(listOf(Entry(System.currentTimeMillis(), date.trim(), person.trim(), note.trim(), amount, kind)) + entries)
                            person = ""; note = ""; amountText = ""; message = "Record saved."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save record") }
                    if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp))
                }
                else -> {
                    Text("Records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val data = JSONArray()
                        entries.forEach { e -> data.put(JSONObject().put("date", e.date).put("person", e.person).put("note", e.note).put("amount", e.amount).put("type", e.kind)) }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json")
                            .putExtra(android.content.Intent.EXTRA_SUBJECT, "My Ledger backup")
                            .putExtra(android.content.Intent.EXTRA_TEXT, data.toString(2))
                        context.startActivity(android.content.Intent.createChooser(intent, "Export ledger backup"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
                    Spacer(Modifier.height(8.dp))
                    if (entries.isEmpty()) Text("No records yet. Add your first entry.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries, key = { it.id }) { e ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(e.note, fontWeight = FontWeight.SemiBold)
                                    Text(listOf(e.date, e.kind, e.person).filter { it.isNotBlank() }.joinToString(" • "))
                                    Text(money(e.amount), style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { save(entries.filterNot { it.id == e.id }) }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopAppBarCompat(title: String) {
    Surface(tonalElevation = 2.dp) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
fun SummaryCard(label: String, value: Double) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(money(value), fontWeight = FontWeight.Bold)
        }
    }
}

fun money(value: Double): String = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "NZ")).format(value)

fun loadEntries(prefs: android.content.SharedPreferences): List<Entry> {
    return try {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Entry(o.optLong("id"), o.optString("date"), o.optString("person"), o.optString("note"), o.optDouble("amount"), o.optString("kind"))
        }
    } catch (_: Exception) { emptyList() }
}
