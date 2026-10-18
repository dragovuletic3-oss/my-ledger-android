package com.myledger.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var selectedPerson by remember { mutableStateOf<String?>(null) }
    var clearRequested by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Owed to me") }
    var kindConfirmed by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var message by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val voiceSession = remember { VoiceSession() }
    var isProcessing by remember { mutableStateOf(false) }

    fun applySpoken(spoken: String) {
        if (spoken.isBlank()) { note = spoken; kindConfirmed = false; return }
        note = spoken
        val parsed = parseEntry(spoken, selectedPerson)
        parsed.amount?.let { amountText = it.toString() }
        kindConfirmed = parsed.kind != null
        parsed.kind?.let { kind = it }
        parsed.person?.let { person = it }
        message = if (parsed.kind == null) "Choose the transaction type, then check the person and amount."
            else "Check the person, type and amount below before saving."

    }

    fun releaseRecognizer() {
        val old = speechRecognizer
        speechRecognizer = null
        // Some vendor services throw during shutdown too.
        try { old?.destroy() } catch (_: RuntimeException) { }
        voiceSession.finish()
        isListening = false
        isProcessing = false
    }

    fun beginListening() {
        if (!voiceSession.begin()) return
        note = ""; amountText = ""; person = selectedPerson.orEmpty(); kindConfirmed = false
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                releaseRecognizer()
                message = "Voice recognition isn't available. Enable a speech recognition service in your phone settings."
                return
            }
            // Create only after microphone permission has been granted, and install
            // the listener before issuing any commands to the service.
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() { isListening = true }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceSession.processing()
                    isListening = false
                    isProcessing = true
                    message = "Processing your words…"
                }
                override fun onError(error: Int) {
                    releaseRecognizer()
                    message = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone access in Settings > Apps > My Ledger > Permissions."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the microphone to try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Check your internet connection, then try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Wait a moment and try again."
                        else -> "Voice entry stopped (code $error). Tap to retry, or type the record."
                    }
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    releaseRecognizer()
                    if (spoken.isBlank()) message = "I didn't catch that. Please try again."
                    else { applySpoken(spoken); tab = "Add entry" }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) message = "Hearing: $partial"
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            isListening = true
            message = "Listening… tap the microphone again to stop."
            recognizer.startListening(intent)
        } catch (_: RuntimeException) {
            releaseRecognizer()
            message = "Voice recognition couldn't start. Check microphone permission and your phone's speech service, then retry."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening()
        else message = "Microphone permission is needed for voice entry. You can still type the record."
    }

    DisposableEffect(Unit) {
        onDispose { releaseRecognizer() }
    }
    LaunchedEffect(tab) {
        releaseRecognizer()
    }
    val income = sumAmount(entries, "Income")
    val expense = sumAmount(entries, "Expense")
    val owedToMe = totalOwedToMe(entries)
    val iOwe = totalIOwe(entries)
    val people = peopleIn(entries)

    fun save(newEntries: List<Entry>) {
        entries = newEntries
        val array = JSONArray()
        newEntries.forEach { e -> array.put(JSONObject().put("id", e.id).put("date", e.date).put("person", e.person).put("note", e.note).put("amount", e.amount).put("kind", e.kind)) }
        prefs.edit().putString("entries", array.toString()).apply()
    }
    fun startVoice() {
        if (isProcessing) {
            message = "Still processing. Please wait for the result."
        } else if (isListening) {
            try {
                voiceSession.processing()
                isListening = false
                isProcessing = true
                message = "Processing your words…"
                speechRecognizer?.stopListening()
            } catch (_: RuntimeException) {
                releaseRecognizer()
                message = "Voice recognition stopped. Tap the microphone to retry."
            }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            beginListening()
        }
    }

    if (clearRequested) AlertDialog(
        onDismissRequest = { clearRequested = false },
        title = { Text("Delete all test records?") },
        text = { Text("This removes every saved entry on this phone. Export a backup first if you need a copy.") },
        confirmButton = { TextButton(onClick = { save(emptyList()); clearRequested = false; selectedPerson = null; message = "All records cleared." }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { clearRequested = false }) { Text("Cancel") } }
    )
    Scaffold(topBar = { TopAppBarCompat("My Ledger 1.2") }, bottomBar = {
        Surface(tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(money(owedToMe - iOwe), fontWeight = FontWeight.Bold)
                }
                Text("Owed to you minus what you owe", style = MaterialTheme.typography.bodySmall)
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Add entry", "Records").forEach { item -> FilterChip(selected = tab == item, onClick = { selectedPerson = null; tab = item }, label = { Text(item) }) }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                "Overview" -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("Your money at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    SummaryCard("Income", income); SummaryCard("Expenses", expense)
                    SummaryCard("People owe me (after payments)", owedToMe)
                    SummaryCard("I owe others (after repayments)", iOwe)
                    if (people.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Balances by person", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        people.forEach { name ->
                            val net = personNet(entries, name)
                            val theyOwe = net.coerceAtLeast(0.0)
                            val iOweThem = (-net).coerceAtLeast(0.0)
                            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { selectedPerson = name; person = name; message = ""; tab = "Person" }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(name, fontWeight = FontWeight.SemiBold)
                                    if (theyOwe != 0.0) Text("They owe you: " + money(theyOwe))
                                    if (iOweThem != 0.0) Text("You owe them: " + money(iOweThem))
                                    if (theyOwe == 0.0 && iOweThem == 0.0) Text("All settled")
                                }
                            }
                        }
                    }
                    Text("Income minus expenses: " + money(income - expense), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text("Records are stored on this device. Export a backup from Records.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                "Person" -> {
                    val name = selectedPerson.orEmpty()
                    val net = personNet(entries, name)
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    SummaryCard(if (net >= 0) "They owe you" else "You owe them", kotlin.math.abs(net))
                    Button(enabled = !isProcessing, onClick = { startVoice() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isListening) "Stop microphone" else if (isProcessing) "Processing…" else "🎙️ Add by voice for $name")
                    }
                    if (message.isNotBlank()) Text(message)
                    TextButton(onClick = { person = name; note = ""; amountText = ""; tab = "Add entry" }) { Text("Type an entry for $name") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries.filter { it.person.trim().equals(name, ignoreCase = true) }, key = { it.id }) { e ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text(e.note, fontWeight = FontWeight.SemiBold)
                                Text("${e.date} • ${e.kind} • ${money(e.amount)}")
                                TextButton(onClick = { save(entries.filterNot { it.id == e.id }) }) { Text("Delete record") }
                            } }
                        }
                    }
                }
                "Add entry" -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
                    Text("Add a record", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        IconButton(enabled = !isProcessing, onClick = { startVoice() }) { Text(if (isListening) "⏹️" else "🎙️", style = MaterialTheme.typography.headlineMedium) }
                        Column(Modifier.weight(1f)) {
                            Text(if (isProcessing) "Processing your words…" else if (isListening) "Listening — tap to stop" else "Tap the microphone to speak", fontWeight = FontWeight.SemiBold)
                            Text("I'll fill in the fields for you to check.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Transaction type")
                    Column {
                        listOf("Owed to me", "Payment received", "I owe", "I paid", "Income", "Expense").chunked(2).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option; kindConfirmed = true }, label = { Text(option) }) }
                            }
                        }
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { applySpoken(it) }, label = { Text("Description / spoken words") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (NZD)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val amount = amountText.toBigDecimalOrNull()?.setScale(2, java.math.RoundingMode.HALF_UP)?.toDouble()
                        if (date.isBlank() || amount == null || !amount.isFinite() || amount <= 0 || note.isBlank()) message = "Enter a valid date, description and positive amount."
                        else if (!kindConfirmed) message = "Choose a transaction type before saving this description."
                        else if (kind !in listOf("Income", "Expense") && person.isBlank()) message = "Enter the person for this lending or repayment entry."
                        else {
                            save(listOf(Entry(System.currentTimeMillis(), date.trim(), person.trim(), note.trim(), amount, kind)) + entries)
                            person = ""; note = ""; amountText = ""; kindConfirmed = false; message = "Record saved."; tab = if (selectedPerson != null) "Person" else "Overview"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save record") }
                    if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp))
                    }
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
                    TextButton(onClick = { clearRequested = true }) { Text("Clear all test records") }
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
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, modifier = Modifier.weight(1f)); Text(money(value), fontWeight = FontWeight.Bold) }
    }
}
fun money(value: Double): String = java.text.NumberFormat.getCurrencyInstance(Locale("en", "NZ")).format(value)
fun loadEntries(prefs: android.content.SharedPreferences): List<Entry> = try {
    val array = JSONArray(prefs.getString("entries", "[]"))
    (0 until array.length()).map { i -> val o = array.getJSONObject(i); Entry(o.optLong("id"), o.optString("date"), o.optString("person"), o.optString("note"), o.optDouble("amount"), o.optString("kind")) }
} catch (_: Exception) { emptyList() }
