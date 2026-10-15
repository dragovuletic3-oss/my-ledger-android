package com.myledger.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Entry(val id: Long, val date: String, val person: String, val note: String, val amount: Double, val kind: String)

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var authMessage by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                if (unlocked) LedgerScreen()
                else LockScreen(authMessage) { authenticate() }
            }
        }
        authenticate()
    }
    private fun authenticate() {
        val manager = BiometricManager.from(this)
        val available = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (available != BiometricManager.BIOMETRIC_SUCCESS) {
            authMessage = "Set up a fingerprint, face unlock, or screen lock in your phone settings to protect My Ledger. For now, access is blocked."
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; authMessage = "" }
            override fun onAuthenticationFailed() { authMessage = "That wasn't recognised. Please try again." }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { authMessage = errString.toString() }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock My Ledger")
            .setSubtitle("Authenticate to view your private records")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun LockScreen(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("My Ledger is locked", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Use your fingerprint or your phone's screen-lock method to open your records.")
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text("Unlock My Ledger") }
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
    var kind by remember { mutableStateOf("Owed to me") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var message by remember { mutableStateOf("") }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) {
            note = spoken
            val amountMatch = Regex("(?:\\$\\s*)?([0-9]+(?:[,.][0-9]{1,2})?)").find(spoken.replace(",", ""))
            if (amountMatch != null) amountText = amountMatch.groupValues[1].replace(",", "")
            val lower = spoken.lowercase(Locale.getDefault())
            kind = when {
                lower.contains("i owe") || lower.contains("i borrowed") -> "I owe"
                lower.contains("i paid") || lower.contains("paid back") || lower.contains("i repaid") -> "I paid"
                lower.contains("paid me") || lower.contains("received") || lower.contains("paid back to me") -> "Payment received"
                lower.contains("owe me") || lower.contains("lent") -> "Owed to me"
                else -> kind
            }
            message = "Check the details below, then save the record."
        }
    }
    val income = entries.filter { it.kind == "Income" }.sumOf { it.amount }
    val expense = entries.filter { it.kind == "Expense" }.sumOf { it.amount }
    val owedToMe = entries.filter { it.kind == "Owed to me" }.sumOf { it.amount } - entries.filter { it.kind == "Payment received" }.sumOf { it.amount }
    val iOwe = entries.filter { it.kind == "I owe" }.sumOf { it.amount } - entries.filter { it.kind == "I paid" }.sumOf { it.amount }

    fun save(newEntries: List<Entry>) {
        entries = newEntries
        val array = JSONArray()
        newEntries.forEach { e -> array.put(JSONObject().put("id", e.id).put("date", e.date).put("person", e.person).put("note", e.note).put("amount", e.amount).put("kind", e.kind)) }
        prefs.edit().putString("entries", array.toString()).apply()
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a transaction, for example: Dave paid me 300 dollars")
        }
        try { speechLauncher.launch(intent) } catch (_: Exception) { message = "Voice recognition isn't available on this phone. You can type the record instead." }
    }

    Scaffold(topBar = { TopAppBarCompat("My Ledger") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Add entry", "Records").forEach { item -> FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) }) }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                "Overview" -> {
                    Text("Your money at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    SummaryCard("Income", income); SummaryCard("Expenses", expense)
                    SummaryCard("People owe me (after payments)", owedToMe)
                    SummaryCard("I owe others (after repayments)", iOwe)
                    Text("Income minus expenses: " + money(income - expense), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text("Records are stored on this device. Export a backup from Records.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                "Add entry" -> {
                    Text("Add a record", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { startVoice() }, modifier = Modifier.fillMaxWidth()) { Text("🎙 Speak a transaction") }
                    Text("Speak naturally, then check the fields before saving.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Transaction type")
                    Column {
                        listOf("Owed to me", "Payment received", "I owe", "I paid", "Income", "Expense").chunked(3).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option) }) }
                            }
                        }
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Description / spoken words") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (NZD)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (date.isBlank() || amount == null || amount <= 0 || note.isBlank()) message = "Enter a valid date, description and amount."
                        else {
                            save(listOf(Entry(System.currentTimeMillis(), date.trim(), person.trim(), note.trim(), amount, kind)) + entries)
                            person = ""; note = ""; amountText = ""; message = "Record saved."; tab = "Overview"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save record") }
                    if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp))
                }
                else -> {
                    Text("Transaction history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val data = JSONArray()
                        entries.forEach { e -> data.put(JSONObject().put("date", e.date).put("person", e.person).put("note", e.note).put("amount", e.amount).put("type", e.kind)) }
                        val intent = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_SUBJECT, "My Ledger backup").putExtra(Intent.EXTRA_TEXT, data.toString(2))
                        context.startActivity(Intent.createChooser(intent, "Export ledger backup"))
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
                                    TextButton(onClick = { save(entries.filterNot { it.id == e.id }) }) { Text("Delete record") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun TopAppBarCompat(title: String) {
    Surface(tonalElevation = 2.dp) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
}
@Composable fun SummaryCard(label: String, value: Double) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(money(value), fontWeight = FontWeight.Bold) }
    }
}
fun money(value: Double): String = java.text.NumberFormat.getCurrencyInstance(Locale("en", "NZ")).format(value)
fun loadEntries(prefs: android.content.SharedPreferences): List<Entry> = try {
    val array = JSONArray(prefs.getString("entries", "[]"))
    (0 until array.length()).map { i -> val o = array.getJSONObject(i); Entry(o.optLong("id"), o.optString("date"), o.optString("person"), o.optString("note"), o.optDouble("amount"), o.optString("kind")) }
} catch (_: Exception) { emptyList() }
