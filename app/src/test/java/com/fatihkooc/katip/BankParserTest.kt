package com.fatihkooc.katip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankParserTest {
    @Test
    fun selectsCustomerPayableAmountInsteadOfGrossBill() {
        val sms = "Elektrik faturası. Tüketim bedeli 1.322,33 TL. Devlet desteği 722,33 TL. Senin ödeyeceğin tutar 600,00 TL."
        val parsed = BankParser.parse(sms)
        assertEquals(600.0, parsed?.amount ?: 0.0, 0.005)
    }

    @Test
    fun doesNotGuessWhenSupportedBillHasNoNetAmountLabel() {
        val sms = "Elektrik faturası: Tüketim bedeli 1.322,33 TL. Devlet desteği 722,33 TL."
        assertNull(BankParser.parse(sms))
    }

    @Test
    fun choosesCardChargeAndIgnoresAvailableBalance() {
        val sms = "Kartınızdan 1.250,00 TL harcama yapıldı. Kullanılabilir bakiyeniz 4.500,00 TL."
        val parsed = BankParser.parse(sms)
        assertEquals(1250.0, parsed?.amount ?: 0.0, 0.005)
    }

    @Test
    fun refusesAmbiguousMultipleAmounts() {
        val sms = "Hesabınız için bildirilen tutarlar: 1.200,00 TL ve 900,00 TL."
        assertNull(BankParser.parse(sms))
    }

    @Test
    fun supportsPayableAmountWithoutCurrencySuffix() {
        val sms = "Fatura bilgisi. Devlet desteği 500 TL. Ödenecek tutar: 275,50"
        val parsed = BankParser.parse(sms)
        assertEquals(275.50, parsed?.amount ?: 0.0, 0.005)
    }

    @Test
    fun suggestsAmountForManualConfirmationWhenBillIsAmbiguous() {
        val sms = "Elektrik faturası: Tüketim bedeli 1.322,33 TL. Devlet desteği 722,33 TL."
        assertEquals(1322.33, BankParser.suggestForConfirmation(sms) ?: 0.0, 0.005)
    }

    @Test
    fun doesNotSuggestForOrdinaryNonFinancialMessage() {
        assertNull(BankParser.suggestForConfirmation("Yarın saat 13:30'da görüşelim."))
    }

    @Test
    fun parsesTurkishAndInternationalNumberFormats() {
        assertEquals(1322.33, BankParser.toDouble("1.322,33") ?: 0.0, 0.005)
        assertEquals(1322.33, BankParser.toDouble("1,322.33") ?: 0.0, 0.005)
        assertEquals(250.5, BankParser.toDouble("250,5") ?: 0.0, 0.005)
    }
}
