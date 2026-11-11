package com.fatihkooc.katip

object BankParser {
    class Parsed(val isIncome: Boolean, val amount: Double, val title: String, val category: String)

    private const val NUM = """(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:,\d{1,2})?|\d+\.\d{1,2})"""
    private val amountA = Regex(NUM + """\s*(?:TL|TRY|₺)""", RegexOption.IGNORE_CASE)
    private val amountB = Regex("""(?:TL|TRY|₺)\s*""" + NUM, RegexOption.IGNORE_CASE)
    private val thousands = Regex("""^\d{1,3}(\.\d{3})+$""")
    private val usFormat = Regex("""^\d{1,3}(,\d{3})+(\.\d{1,2})?$""")

    private val reject = listOf(
        "onay kodu", "dogrulama", " otp ", "kampanya", "indirim", "firsat",
        "tek kullanimlik", "kodunuz"
    )
    private val rejectWeak = listOf("sifre")

    // E-postada bilgilendirme/pazarlama mesajlarını elemek için ek yasaklar
    private val rejectMail = listOf(
        "ekstre", "son odeme", "asgari", "faiz", "teklif", "kredi limit", "borcunuz",
        "hatirlat", "bulten", "uyelik", "sozlesme"
    )

    private val strongSpend = listOf(
        "kartinizdan", "hesabinizdan", "harcama", "alisveris", "pos ", "cekildi",
        "gonderildi", "provizyon", "borclandirildi", "satin alma",
        "temassiz", "mobil odeme", "odemeniz alindi", "odemeniz basariyla",
        "odemeniz gerceklesti", "odemeniz tamamlandi", "fatura odemesi"
    )
    private val incomeWords = listOf(
        "hesabiniza", "yatirildi", "gelen havale", "gelen eft", "maas", "alacak",
        "para girisi", "tahsil", "odeme alindi"
    )
    private val bankPackages = listOf(
        "garanti", "isbank", "akbank", "ziraat", "yapikredi", "vakif", "halkbank", "denizbank",
        "qnb", "teb", "enpara", "papara", "fibabanka", "ingbank", "kuveytturk", "albaraka",
        "odeabank", "sekerbank", "hsbc", "tosla", "paycell", "ininal", "finansbank", "burgan",
        "anadolubank", "emlak", "turkiyefinans", "colendi",
        // Cüzdan uygulamaları (telefondan temassız ödeme)
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

    /** "1.250,50", "1,250.50", "1250.50", "250,5" -> Double */
    fun toDouble(s: String): Double? {
        val x = s.trim()
        if (x.isEmpty()) return null
        return when {
            usFormat.matches(x) -> x.replace(",", "").toDoubleOrNull()
            x.contains(",") -> x.replace(".", "").replace(",", ".").toDoubleOrNull()
            thousands.matches(x) -> x.replace(".", "").toDoubleOrNull()
            else -> x.toDoubleOrNull()
        }
    }

    fun isMailPackage(pkg: String): Boolean = mailPackages.contains(pkg.lowercase())

    /** Bildirimin hangi uygulamadan geldiği: banka/cüzdan, SMS veya e-posta uygulaması mı? */
    fun acceptPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return smsPackages.contains(p) || mailPackages.contains(p) || bankPackages.any { p.contains(it) }
    }

    /** fromMail = true ise daha katı kurallar uygulanır (pazarlama e-postalarını kaydetmemek için). */
    fun parse(text: String, fromMail: Boolean = false): Parsed? {
        val a = amountA.find(text)
        val b = amountB.find(text)
        val m = listOfNotNull(a, b).minByOrNull { it.range.first } ?: return null
        val value = toDouble(m.groupValues[1]) ?: return null
        if (value <= 0.0) return null

        val n = Categories.norm(text)
        if (reject.any { n.contains(it) }) return null
        if (fromMail && rejectMail.any { n.contains(it) }) return null
        val hasSpend = strongSpend.any { n.contains(it) }
        if (!hasSpend && rejectWeak.any { n.contains(it) }) return null

        val isIncome = when {
            n.contains("iade") -> true
            hasSpend -> false
            incomeWords.any { n.contains(it) } -> true
            fromMail -> return null // e-postada belirsiz "işlem/ödeme" kelimesi yetmez
            n.contains("odeme") || n.contains("islem") -> false
            else -> return null
        }
        val (cat, merchant) = Categories.match(text, isIncome)
        val title = merchant ?: if (isIncome) "Gelen para" else "Banka/kart harcaması"
        return Parsed(isIncome, value, title, cat)
    }
}
