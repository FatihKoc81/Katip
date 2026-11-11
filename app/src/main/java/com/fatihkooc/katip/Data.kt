package com.fatihkooc.katip

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Txn(
    val id: Long,
    val isIncome: Boolean,
    val title: String,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long,
    val source: String // manuel, bildirim, sms, deneme
)

object Categories {
    val expense = listOf("Market", "Yemek", "Ulaşım", "Yakıt", "Fatura", "Alışveriş", "Sağlık", "Eğlence", "Diğer")
    val income = listOf("Maaş", "Ek gelir", "İade", "Diğer gelir")

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Market" to listOf(" bim ", " a101 ", " sok ", "migros", "carrefour", "market", " manav ", " kasap ", "hakmar", " macro ", "happy center", "bizim toptan", "getir"),
        "Yemek" to listOf("restoran", "restaurant", "lokanta", " cafe ", " kafe ", "kahve", "starbucks", "yemeksepeti", "burger", "doner", "pizza", "pastane", "firin", "simit", "kebap", "kofte", "lahmacun"),
        "Ulaşım" to listOf("otobus", " metro ", "taksi", " uber ", "bitaksi", "havaist", "pegasus", " thy ", "turk hava", "otopark", " hgs ", " ogs ", "istanbulkart", "akbil", "kentkart", " marti "),
        "Yakıt" to listOf("akaryakit", "petrol", "benzin", "motorin", " opet ", " shell ", " bp ", "aytemiz", "lukoil", "alpet", "kadoil", "turkpetrol"),
        "Fatura" to listOf("fatura", "elektrik", "dogalgaz", "dogal gaz", "turkcell", "vodafone", "turk telekom", "ttnet", "superonline", "aidat", " kira ", "enerjisa", "igdas", " iski ", " aski ", "tedas", "bedas", "digiturk"),
        "Alışveriş" to listOf("trendyol", "hepsiburada", " n11 ", "amazon", "lc waikiki", " lcw ", "defacto", "koton", " mavi ", " zara ", "teknosa", "mediamarkt", " vatan ", "ikea", "boyner", "pazarama", "ciceksepeti", " flo ", "decathlon", "english home"),
        "Sağlık" to listOf("eczane", "hastane", "klinik", "saglik", "doktor", "tip merkezi", "optik", "medikal", "laboratuvar"),
        "Eğlence" to listOf("sinema", "netflix", "spotify", "youtube", "disney", "exxen", "steam", "playstation", "tiyatro", "biletix", "passo", "google play", "app store", "konser", "cinemaximum")
    )

    /** Türkçe karakterleri sadeleştirir, harf/rakam dışını boşluk yapar, başa-sona boşluk koyar. */
    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    'ç', 'Ç' -> 'c'
                    'ğ', 'Ğ' -> 'g'
                    'ı', 'I', 'İ' -> 'i'
                    'ö', 'Ö' -> 'o'
                    'ş', 'Ş' -> 's'
                    'ü', 'Ü' -> 'u'
                    else -> ch.lowercaseChar()
                }
            )
        }
        return " " + sb.toString().replace(Regex("[^a-z0-9]+"), " ").trim() + " "
    }

    /** (kategori, tanınan işyeri adı veya null) */
    fun match(text: String, income: Boolean): Pair<String, String?> {
        val t = norm(text)
        if (income) {
            return when {
                t.contains("maas") -> "Maaş" to null
                t.contains("iade") -> "İade" to null
                else -> "Ek gelir" to null
            }
        }
        for ((cat, kws) in rules) {
            for (kw in kws) {
                if (t.contains(kw)) return cat to kw.trim().replaceFirstChar { it.titlecase() }
            }
        }
        return "Diğer" to null
    }
}

class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("katip_local", Context.MODE_PRIVATE)

    companion object {
        private val LOCK = Any()
        private const val KEY = "txns"
        private const val OLD_KEY = "expenses"
        private const val BUDGET = "budget"
        private const val DUP_WINDOW = 3 * 60 * 1000L
    }

    fun load(): List<Txn> = synchronized(LOCK) { read().sortedByDescending { it.date } }

    private fun read(): List<Txn> {
        val raw = prefs.getString(KEY, null) ?: prefs.getString(OLD_KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Txn(
                    o.getLong("id"), o.optBoolean("income", false), o.getString("title"),
                    o.getDouble("amount"), o.optString("category", "Diğer"), o.optString("note"),
                    o.getLong("date"), o.optString("source", "manuel")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<Txn>) {
        val a = JSONArray()
        list.forEach { t ->
            a.put(JSONObject().apply {
                put("id", t.id); put("income", t.isIncome); put("title", t.title)
                put("amount", t.amount); put("category", t.category); put("note", t.note)
                put("date", t.date); put("source", t.source)
            })
        }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    fun add(t: Txn) = synchronized(LOCK) {
        val list = read()
        var id = t.id
        while (list.any { it.id == id }) id++
        write(list + t.copy(id = id))
    }

    /** Bildirim/SMS/e-postadan gelen kaydı ekler; aynı işlem pencere süresi içinde tekrar gelirse eklemez. */
    fun addAuto(t: Txn, window: Long = DUP_WINDOW): Boolean = synchronized(LOCK) {
        val list = read()
        val dup = list.any {
            it.source != "manuel" && it.isIncome == t.isIncome &&
                Math.abs(it.amount - t.amount) < 0.005 && Math.abs(it.date - t.date) < window
        }
        if (dup) return@synchronized false
        var id = t.id
        while (list.any { it.id == id }) id++
        write(list + t.copy(id = id))
        true
    }

    fun update(t: Txn) = synchronized(LOCK) { write(read().map { if (it.id == t.id) t else it }) }

    fun delete(id: Long) = synchronized(LOCK) { write(read().filterNot { it.id == id }) }

    fun clear() = synchronized(LOCK) {
        prefs.edit().remove(OLD_KEY).putString(KEY, "[]").apply()
    }

    fun budget(): Double = prefs.getString(BUDGET, null)?.toDoubleOrNull() ?: 0.0

    fun setBudget(v: Double) {
        prefs.edit().putString(BUDGET, v.toString()).apply()
    }

    fun listen(cb: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) cb() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return l
    }

    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(l)
    }
}
