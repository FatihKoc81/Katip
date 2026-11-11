package com.fatihkooc.katip

object BankParser {
    class Parsed(val isIncome: Boolean, val amount: Double, val title: String, val category: String)

    private const val NUM = """(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[,.]\d{1,2})?|\d+\.\d{1,2})"""
    private val amountAfterCurrency = Regex(NUM + """\s*(?:TL|TRY|₺)""", RegexOption.IGNORE_CASE)
    private val amountBeforeCurrency = Regex("""(?:TL|TRY|₺)\s*""" + NUM, RegexOption.IGNORE_CASE)
    private val thousands = Regex("""^\d{1,3}(\.\d{3})+$""")
    private val usFormat = Regex("""^\d{1,3}(,\d{3})+(\.\d{1,2})?$""")

    private data class Candidate(val value: Double, val start: Int, val end: Int, val source: String)

    // En yüksek öncelik: müşterinin gerçekten ödeyeceği / hesabından çekilecek net tutar.
    // Farklı kurumlar aynı kavramı farklı kelimelerle ifade edebildiği için eş anlamlı etiketler kullanılır.
    private val payableLabels = listOf(
        Regex("""(?:senin\s+ödeyeceğin(?:\s+tutar)?|sizin\s+ödeyeceğiniz(?:\s+tutar)?|ödeyeceğiniz\s+tutar|müşterinin\s+ödeyeceği(?:\s+tutar)?|ödenecek(?:\s+fatura)?\s+(?:tutar[ıi]?|miktar[ıi]?|bedel[ıi]?)|ödenmesi\s+gereken(?:\s+fatura)?\s+tutar[ıi]?|ödemesi\s+gereken(?:\s+fatura)?\s+tutar[ıi]?|net\s+(?:ödeme|fatura)\s+tutar[ıi]?|tahsil\s+edilecek\s+tutar|ödenecek\s+toplam(?:\s+tutar)?)\D{0,80}""" + NUM + """\s*(?:TL|TRY|₺)?""", RegexOption.IGNORE_CASE),
        Regex(NUM + """\s*(?:TL|TRY|₺)?\s*(?:ödenecek\s+tutar|ödenmesi\s+gereken\s+tutar|müşterinin\s+ödeyeceği\s+tutar)""", RegexOption.IGNORE_CASE)
    )

    private val reject = listOf(
        "onay kodu", "dogrulama", " otp ", "kampanya", "indirim", "firsat",
        "tek kullanimlik", "kodunuz"
    )
    private val rejectMail = listOf(
        "ekstre", "asgari tutar", "faiz orani", "teklif", "kredi limit",
        "hatirlatma", "bulten", "uyelik", "sozlesme"
    )
    private val strongSpend = listOf(
        "kartinizdan", "hesabinizdan", "harcama", "alisveris", "pos ",
        "cekildi", "gonderildi", "provizyon", "borclandirildi", "satin alma",
        "temassiz", "mobil odeme", "odemeniz alindi", "odemeniz basariyla",
        "odemeniz gerceklesti", "odemeniz tamamlandi", "fatura odemesi",
        "islem tutari", "harcama tutari", "tahsil edildi"
    )
    private val incomeWords = listOf(
        "hesabiniza", "yatirildi", "gelen havale", "gelen eft", "maas", "alacak",
        "para girisi", "tahsilat", "odeme alindi"
    )
    private val positiveAmountContext = listOf(
        "islem tutari", "harcama tutari", "odeme tutari", "cekilen tutar",
        "kartinizdan", "hesabinizdan", "tahsil edildi", "tahsilat tutari",
        "alisveris tutari", "odemeniz", "harcama", "provizyon", "satin alma",
        "fatura odemesi", "islem gerceklesti", "islem basarili"
    )
    private val negativeAmountContext = listOf(
        "bakiye", "kalan bakiye", "kullanilabilir limit", "kredi limiti",
        "devlet destegi", "enerji destegi", "indirim tutari", "kdv",
        "vergi tutari", "referans no", "islem no", "musteri no",
        "tesisat no", "abone no", "son odeme tarihi", "tuketim bedeli",
        "toplam tuketim", "onceki borc", "gecikme faizi"
    )
    private val utilityWords = listOf(
        "elektrik", "dogalgaz", "dogal gaz", "su faturasi", "elektrik faturasi",
        "enerji destegi", "devlet destegi", "tuketim bedeli", "sepas",
        "bedas", "ayedas", "enerjisa", "igdas", "iski", "aski", "uludag elektrik"
    )
    private val bankPackages = listOf(
        "garanti", "isbank", "akbank", "ziraat", "yapikredi", "vakif", "halkbank", "denizbank",
        "qnb", "teb", "enpara", "papara", "fibabanka", "ingbank", "kuveytturk", "albaraka",
        "odeabank", "sekerbank", "hsbc", "tosla", "paycell", "ininal", "finansbank", "burgan",
        "anadolubank", "emlak", "turkiyefinans", "colendi", "pozitron.iscep",
        "samsung.android.spay", "walletnfcrel"
    )
    private val smsPackages = listOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.android.mms", "com.hihonor.mms", "com.huawei.message", "com.android.messaging"
    )
    private val mailPackages = listOf(
        "com.google.android.gm", "com.google.android.gm.lite",
        "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email.provider", "com.android.email", "ru.yandex.mail"
    )

    fun toDouble(s: String): Double? {
        val x = s.trim().replace(" ", "")
        if (x.isEmpty()) return null
        return when {
            usFormat.matches(x) -> x.replace(",", "").toDoubleOrNull()
            x.contains(",") -> x.replace(".", "").replace(",", ".").toDoubleOrNull()
            thousands.matches(x) -> x.replace(".", "").toDoubleOrNull()
            else -> x.toDoubleOrNull()
        }
    }

    fun isMailPackage(pkg: String): Boolean = mailPackages.contains(pkg.lowercase())

    fun acceptPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return smsPackages.contains(p) || mailPackages.contains(p) || bankPackages.any { p.contains(it) }
    }

    private fun normalize(s: String): String = Categories.norm(s)

    private fun candidates(text: String): List<Candidate> {
        val found = mutableListOf<Candidate>()
        fun collect(regex: Regex, source: String) {
            regex.findAll(text).forEach { m ->
                val amount = toDouble(m.groupValues[1]) ?: return@forEach
                if (amount > 0.0 && amount.isFinite()) {
                    val start = m.groups[1]?.range?.first ?: m.range.first
                    val end = m.groups[1]?.range?.last?.plus(1) ?: m.range.last + 1
                    if (found.none { it.start == start && it.end == end }) {
                        found += Candidate(amount, start, end, source)
                    }
                }
            }
        }
        collect(amountAfterCurrency, "currency-after")
        collect(amountBeforeCurrency, "currency-before")
        return found.sortedBy { it.start }
    }

    private fun explicitPayable(text: String): List<Candidate> {
        val found = mutableListOf<Candidate>()
        payableLabels.forEachIndexed { index, regex ->
            regex.findAll(text).forEach { m ->
                val value = toDouble(m.groupValues[1]) ?: return@forEach
                if (value > 0.0 && value.isFinite()) {
                    val start = m.groups[1]?.range?.first ?: m.range.first
                    val end = m.groups[1]?.range?.last?.plus(1) ?: m.range.last + 1
                    if (found.none { kotlin.math.abs(it.value - value) < 0.005 }) {
                        found += Candidate(value, start, end, "payable-label-$index")
                    }
                }
            }
        }
        return found
    }

    private fun score(candidate: Candidate, text: String): Int {
        val from = (candidate.start - 35).coerceAtLeast(0)
        val to = (candidate.end + 25).coerceAtMost(text.length)
        val context = normalize(text.substring(from, to))
        var score = 0
        positiveAmountContext.forEach { if (context.contains(it)) score += 3 }
        negativeAmountContext.forEach { if (context.contains(it)) score -= 5 }
        return score
    }

    /**
     * Güvenli ayrıştırma:
     * - Önce açık net/ödenecek tutar etiketleri.
     * - Sonra bağlamı güçlü işlem tutarı adayları.
     * - Birden fazla rakam varsa ve kazanan açık değilse kayıt üretme.
     * Asla yalnızca mesajdaki ilk sayıyı seçme.
     */
    fun parse(text: String, fromMail: Boolean = false): Parsed? {
        if (text.isBlank()) return null
        val n = normalize(text)
        if (reject.any { n.contains(it) }) return null
        if (fromMail && rejectMail.any { n.contains(it) }) return null

        val payable = explicitPayable(text)
        val allAmounts = candidates(text)
        val isUtilityBill = utilityWords.any { n.contains(it) }

        val selected: Candidate = when {
            payable.size == 1 -> payable.first()
            payable.size > 1 -> return null // Bir mesajda birden fazla farklı net tutar varsa insan onayı gerekir.
            isUtilityBill -> return null // Destek/vergili faturalarda brüt tutarı tahmin etme.
            allAmounts.isEmpty() -> return null
            allAmounts.size == 1 -> allAmounts.first()
            else -> {
                val ranked = allAmounts.map { it to score(it, text) }.sortedByDescending { it.second }
                if (ranked.first().second < 3) return null
                if (ranked.size > 1 && ranked[0].second == ranked[1].second) return null
                if (ranked[0].second - ranked[1].second < 3) return null
                ranked.first().first
            }
        }

        val value = selected.value
        val hasSpend = payable.isNotEmpty() || strongSpend.any { n.contains(it) }
        val isIncome = when {
            n.contains("iade") -> true
            hasSpend -> false
            incomeWords.any { n.contains(it) } -> true
            fromMail -> return null
            n.contains("odeme") || n.contains("islem") -> false
            else -> return null
        }
        val (cat, merchant) = Categories.match(text, isIncome)
        val title = merchant ?: if (isIncome) "Gelen para" else "Banka/kart harcaması"
        return Parsed(isIncome, value, title, cat)
    }
}
