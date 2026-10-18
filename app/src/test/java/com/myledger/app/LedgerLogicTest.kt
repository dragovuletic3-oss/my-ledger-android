package com.myledger.app

import org.junit.Assert.*
import org.junit.Test

class LedgerLogicTest {
    private fun entry(text: String, fallback: String? = null): Entry {
        val p = parseEntry(text, fallback)
        return Entry(0, "2026-10-18", requireNotNull(p.person), text, requireNotNull(p.amount), requireNotNull(p.kind))
    }
    @Test fun danLendingAndRepaymentLeaveOneHundredOwedToMe() {
        val loan = entry("I gave Dan three hundred dollars")
        val payment = entry("Dan gave me back two hundred dollars")
        assertEquals("Owed to me", loan.kind)
        assertEquals("Payment received", payment.kind)
        val entries = listOf(loan, payment)
        assertEquals(100.0, totalOwedToMe(entries), 0.001)
        assertEquals(0.0, totalIOwe(entries), 0.001)
        assertEquals(0.0, sumAmount(entries, "Income"), 0.001)
    }
    @Test fun borrowingAndRepayingHaveOppositeDirection() {
        val entries = listOf(entry("I owe Dan $300"), entry("I paid Dan back $200"))
        assertEquals(100.0, totalIOwe(entries), 0.001)
        assertEquals(0.0, totalOwedToMe(entries), 0.001)
        assertEquals("I owe", entry("Dan lent me $300").kind)
    }
    @Test fun supportedPaymentPhrasesMatch() {
        listOf("Dan paid me 200", "Dan just paid me 200", "Dan repaid me 200", "Dan gave me back 200", "Dan paid me back 200", "Dan paid back 200").forEach {
            val p = parseEntry(it)
            assertEquals(it, "Payment received", p.kind)
            assertEquals(it, "Dan", p.person)
        }
    }
    @Test fun lendingPhrasesAndMultiwordNames() {
        listOf("I lent Dan 300", "I loaned Dan 300", "Dan owes me 300", "Dan borrowed from me 300").forEach {
            assertEquals(it, "Owed to me", parseEntry(it).kind)
        }
        assertEquals("Mary Jane", parseEntry("I gave Mary Jane $300").person)
        assertEquals("Mary Jane", parseEntry("Mary Jane gave me back $200").person)
        assertEquals("Dan", parseEntry("I paid back Dan 200").person)
        assertEquals("Dan", parseEntry("I gave Dan fifteen dollars").person)
        assertEquals(15.0, entry("I gave Dan fifteen dollars").amount, 0.001)
    }
    @Test fun personPageSupportsPronouns() {
        val p = parseEntry("He paid me back 200", "Dan")
        assertEquals("Dan", p.person)
        assertEquals("Payment received", p.kind)
        assertEquals("Dan", parseEntry("I gave him 300", "Dan").person)
    }
    @Test fun unrelatedDescriptionsNeverGuessCategory() {
        assertNull(parseEntry("Groceries 30").kind)
        assertNull(parseEntry("something happened").amount)
    }
    @Test fun namesAreCaseInsensitiveAndOtherPeoplesDebtsDoNotCancel() {
        val entries = listOf(entry("I gave Dan 300"), entry("dan paid me 200"), entry("I owe Jane 50"))
        assertEquals(2, peopleIn(entries).size)
        assertEquals(100.0, totalOwedToMe(entries), 0.001)
        assertEquals(50.0, totalIOwe(entries), 0.001)
        assertEquals(50.0, totalOwedToMe(entries) - totalIOwe(entries), 0.001)
    }
    @Test fun decimalsAreSummedAsCentsAndThousandsAreParsed() {
        assertEquals(1234.56, entry("I gave Dan $1,234.56").amount, 0.001)
        val entries = listOf(entry("I gave Dan 0.10"), entry("I gave Dan 0.20"), entry("Dan paid me 0.30"))
        assertEquals(0.0, personNet(entries, "Dan"), 0.0)
    }
    @Test fun overpaymentAppearsAsCreditInsteadOfNegativeDebt() {
        val entries = listOf(entry("I gave Dan 100"), entry("Dan paid me 120"))
        assertEquals(0.0, totalOwedToMe(entries), 0.001)
        assertEquals(20.0, totalIOwe(entries), 0.001)
    }
}
