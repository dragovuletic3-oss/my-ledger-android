package com.myledger.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class Entry(val id: Long, val date: String, val person: String, val note: String, val amount: Double, val kind: String)
data class ParsedEntry(val person: String?, val amount: Double?, val kind: String?)

/** Shared by typed descriptions and microphone results. An unknown sentence stays unclassified. */
fun parseEntry(text: String, defaultPerson: String? = null): ParsedEntry {
    val clean = text.trim().replace(Regex("[,.!?]+$"), "").replace(Regex("\\s+"), " ")
    val name = "([\\p{L}][\\p{L}'’\\-]*(?: [\\p{L}][\\p{L}'’\\-]*)*?)"
    val amountStart = "(?=\\s+(?:\\$|[0-9]|zero\\b|one\\b|two\\b|three\\b|four\\b|five\\b|six\\b|seven\\b|eight\\b|nine\\b|ten\\b|twenty\\b|thirty\\b|forty\\b|fifty\\b|sixty\\b|seventy\\b|eighty\\b|ninety\\b)|$)"
    val rules = listOf(
        Pair("^i (?:gave|lent|loaned) $name$amountStart", "Owed to me"),
        Pair("^$name (?:owes? me|borrowed(?: from me)?)\\b", "Owed to me"),
        Pair("^i (?:owe|borrowed from) $name$amountStart", "I owe"),
        Pair("^$name (?:gave|lent|loaned) me (?!back\\b)", "I owe"),
        Pair("^$name (?:just )?(?:paid me(?: back)?|repaid me|gave me back|paid back to me)\\b", "Payment received"),
        Pair("^i (?:paid back|repaid|paid) $name(?: back)?$amountStart", "I paid"),
        Pair("^$name (?:just )?paid (?:me )?back\\b", "Payment received")
    )
    var who: String? = null
    var kind: String? = null
    for ((pattern, category) in rules) {
        val match = Regex(pattern, RegexOption.IGNORE_CASE).find(clean)
        if (match != null) { who = match.groupValues[1]; kind = category; break }
    }
    // On an individual's page the name may be omitted.
    if (kind == null && !defaultPerson.isNullOrBlank()) {
        kind = when {
            Regex("^(?:he|she|they) (?:just )?(?:paid me(?: back)?|gave me back|repaid me)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean) -> "Payment received"
            Regex("^i (?:gave|lent|loaned) (?:him|her|them)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean) -> "Owed to me"
            Regex("^i (?:paid|repaid)(?: back)? (?:him|her|them)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean) -> "I paid"
            else -> null
        }
    }
    if (who?.lowercase(Locale.ROOT) in listOf("him", "her", "them")) who = defaultPerson
    val numeric = Regex("(?:\\$\\s*)?(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)(?!\\d)").find(clean)
    val amount = numeric?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: wordAmount(clean)
    return ParsedEntry(who?.trim() ?: defaultPerson?.takeIf { it.isNotBlank() }, amount, kind)
}

private fun wordAmount(text: String): Double? {
    val values = mapOf("zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    var group = 0L; var total = 0L; var found = false
    for (word in text.lowercase(Locale.ROOT).replace('-', ' ').split(Regex("\\s+"))) {
        when {
            values.containsKey(word) -> { group += values.getValue(word); found = true }
            word == "hundred" && found -> group *= 100
            word == "thousand" && found -> { total += group * 1000; group = 0 }
            word == "and" && found -> Unit
            found -> break
        }
    }
    return if (found) (total + group).toDouble() else null
}

private fun cents(value: Double): Long = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
fun sumAmount(entries: List<Entry>, kind: String): Double = entries.filter { it.kind == kind }.sumOf { cents(it.amount) } / 100.0
fun personNet(entries: List<Entry>, person: String): Double = entries.filter { it.person.trim().equals(person.trim(), ignoreCase = true) }.sumOf {
    when (it.kind) { "Owed to me", "I paid" -> cents(it.amount); "I owe", "Payment received" -> -cents(it.amount); else -> 0L }
} / 100.0
fun peopleIn(entries: List<Entry>): List<String> = entries.map { it.person.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }
fun totalOwedToMe(entries: List<Entry>): Double = peopleIn(entries).sumOf { cents(personNet(entries, it).coerceAtLeast(0.0)) } / 100.0
fun totalIOwe(entries: List<Entry>): Double = peopleIn(entries).sumOf { cents((-personNet(entries, it)).coerceAtLeast(0.0)) } / 100.0
