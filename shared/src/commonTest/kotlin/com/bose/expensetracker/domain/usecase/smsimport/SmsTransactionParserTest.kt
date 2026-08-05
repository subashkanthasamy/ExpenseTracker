package com.bose.expensetracker.domain.usecase.smsimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The parser is shared, so these cover both Android's automatic SMS import and the iOS
 * paste/share flow. It had no tests before.
 */
class SmsTransactionParserTest {

    private val parser = SmsTransactionParser()
    private val now = 1_754_000_000_000L

    private fun parse(body: String, sender: String = "HDFCBK") =
        parser.parse(sender = sender, body = body, receivedTimestamp = now)

    // --- debits are imported -------------------------------------------------

    @Test
    fun parsesCardSpendWithMerchantAndCard() {
        val result = parse("Rs.450.00 spent on HDFC Bank Card x1234 at SWIGGY on 05-08-26. Not you? Call 18002586161")
        assertNotNull(result)
        assertEquals(450.0, result.amount)
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals("1234", result.cardOrAccount)
        assertNotNull(result.merchant)
    }

    @Test
    fun parsesRupeeSymbolAndThousandsSeparator() {
        val result = parse("₹1,299.50 debited from a/c XX9876 towards AMAZON PAY on 05-08-26")
        assertNotNull(result)
        assertEquals(1299.50, result.amount)
        assertEquals("9876", result.cardOrAccount)
    }

    @Test
    fun parsesInrPrefix() {
        val result = parse("INR 250 debited for UPI txn to BLINKIT ref 4455")
        assertNotNull(result)
        assertEquals(250.0, result.amount)
    }

    @Test
    fun parsesAmountWithoutDecimals() {
        val result = parse("Rs 75 spent at CHAI POINT via UPI")
        assertNotNull(result)
        assertEquals(75.0, result.amount)
    }

    // --- things that must NOT become expenses --------------------------------

    @Test
    fun creditsAreIgnored() {
        // Money coming in is not an expense; the parser drops it.
        assertNull(parse("Rs.5,000.00 credited to a/c XX1234 on 05-08-26 by NEFT"))
    }

    @Test
    fun refundsAreIgnored() {
        assertNull(parse("Refund of Rs.299.00 processed to your card x1234 by AMAZON"))
    }

    @Test
    fun otpMessagesAreIgnored() {
        assertNull(parse("123456 is your OTP for login. Do not share it with anyone."))
    }

    @Test
    fun promotionalMessagesWithoutAnAmountAreIgnored() {
        assertNull(parse("Get 10% cashback on your next order! Use code SAVE10. T&C apply."))
    }

    @Test
    fun messageWithNoAmountIsIgnored() {
        assertNull(parse("Your account statement for July is ready to view."))
    }

    // --- category matching ---------------------------------------------------

    @Test
    fun categoryMatcherRecognisesAFoodMerchant() {
        val matcher = SmsCategoryMatcher()
        val result = parse("Rs.450.00 spent on card x1234 at SWIGGY on 05-08-26")
        assertNotNull(result)
        val category = matcher.matchCategory(merchant = result.merchant, smsBody = result.rawMessage)
        // Should land on something food-related rather than nothing at all.
        assertNotNull(category)
    }
}
