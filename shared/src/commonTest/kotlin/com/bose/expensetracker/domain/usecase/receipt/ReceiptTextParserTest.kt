package com.bose.expensetracker.domain.usecase.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptTextParserTest {

    // --- amount -------------------------------------------------------------

    @Test
    fun prefersAmountOnATotalLine() {
        val text = """
            BIG BAZAAR
            Milk 45.00
            Bread 30.00
            Subtotal 75.00
            Total 82.50
        """.trimIndent()
        assertEquals(82.50, ReceiptTextParser.extractAmount(text))
    }

    @Test
    fun subtotalDoesNotMasqueradeAsTotal() {
        // "subtotal" contains "total"; the real total must still win.
        assertEquals(82.50, ReceiptTextParser.extractAmount("Subtotal 75.00\nTotal 82.50"))
    }

    @Test
    fun grandTotalOutranksTotal() {
        assertEquals(99.00, ReceiptTextParser.extractAmount("Total 80.00\nGrand Total 99.00"))
    }

    @Test
    fun fallsBackToLargestAmountWhenNoTotalLine() {
        val text = "Item A 12.00\nItem B 340.75\nItem C 9.99"
        assertEquals(340.75, ReceiptTextParser.extractAmount(text))
    }

    @Test
    fun stripsThousandsSeparators() {
        assertEquals(1234.56, ReceiptTextParser.extractAmount("Grand Total 1,234.56"))
    }

    @Test
    fun returnsNullWhenNoAmountPresent() {
        assertNull(ReceiptTextParser.extractAmount("no numbers with cents here"))
    }

    // --- date ---------------------------------------------------------------

    private val millisPerDay = 86_400_000L

    @Test
    fun parsesUnambiguousMonthDayYear() {
        // 2026-08-05 is 20_670 days after the epoch.
        assertEquals(20_670L * millisPerDay, ReceiptTextParser.extractDate("Date: 08/05/2026"))
    }

    @Test
    fun parsesEpochItself() {
        assertEquals(0L, ReceiptTextParser.extractDate("01/01/1970"))
    }

    @Test
    fun handlesTwoDigitYears() {
        assertEquals(
            ReceiptTextParser.extractDate("01/01/2026"),
            ReceiptTextParser.extractDate("01/01/26")
        )
    }

    @Test
    fun fallsBackToDayMonthWhenFirstPartCannotBeAMonth() {
        // 25 cannot be a month, so this must read as 25 March.
        assertEquals(
            ReceiptTextParser.extractDate("03/25/2026"),
            ReceiptTextParser.extractDate("25/03/2026")
        )
    }

    @Test
    fun leapDayIsAccepted() {
        // 2028-02-29 is 21_243 days after the epoch.
        assertEquals(21_243L * millisPerDay, ReceiptTextParser.extractDate("02/29/2028"))
    }

    @Test
    fun rejectsImpossibleDate() {
        assertNull(ReceiptTextParser.extractDate("02/30/2026"))
    }

    @Test
    fun returnsNullWhenNoDatePresent() {
        assertNull(ReceiptTextParser.extractDate("Total 45.00"))
    }

    // --- merchant -----------------------------------------------------------

    @Test
    fun merchantIsFirstNonBlankLine() {
        assertEquals("CAFE COFFEE DAY", ReceiptTextParser.extractMerchant("\n\n  CAFE COFFEE DAY  \nBill 120.00"))
    }

    @Test
    fun parseCombinesAllThree() {
        val result = ReceiptTextParser.parse("SWIGGY\nOrder 01/15/2026\nTotal 499.00")
        assertEquals("SWIGGY", result.merchant)
        assertEquals(499.00, result.amount)
        assertEquals(20_468L * millisPerDay, result.date)
    }

    @Test
    fun parsesRealVisionOcrOutput() {
        // Verbatim output of Apple's Vision framework on a rendered receipt, after the iOS
        // side reconstructs visual rows (ReceiptScanService.reconstructLines).
        val ocr = """
            CAFE COFFEE DAY
            Date: 01/15/2026
            Latte 180.00
            Sandwich 220.00
            Subtotal 400.00
            Total 448.00
        """.trimIndent()
        val result = ReceiptTextParser.parse(ocr)
        assertEquals("CAFE COFFEE DAY", result.merchant)
        assertEquals(448.00, result.amount)          // not the 400.00 subtotal
        assertEquals(20_468L * millisPerDay, result.date)
    }
}
