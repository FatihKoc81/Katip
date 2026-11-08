package com.fatihkooc.katip.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fatihkooc.katip.data.TransactionEntity

class KatipNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""

        // Desteklenen banka ve cüzdan uygulamalarının paket filtreleri
        if (isSupportedBankingApp(packageName)) {
            val parsedExpense = parseNotification(title, text)
            if (parsedExpense != null) {
                saveToDatabase(parsedExpense)
            }
        }
    }

    private fun isSupportedBankingApp(pkg: String): Boolean {
        val supportedApps = listOf(
            "com.garanti.cepsubesi",
            "com.akbank.android.apps.axess_mobil",
            "com.ykb.android",
            "tr.com.ziraat.ziraatmobil",
            "com.enpara.enparabilgi",
            "com.denizbank.mobildeniz"
        )
        return supportedApps.contains(pkg)
    }

    private fun parseNotification(title: String, text: String): TransactionEntity? {
        if (text.contains("TL", ignoreCase = true) || text.contains("tutar", ignoreCase = true)) {
            return TransactionEntity(
                merchant = title.ifEmpty { "Mobil Harcama" },
                amount = extractAmountFromText(text),
                category = "Genel",
                date = System.currentTimeMillis(),
                source = "Bildirim"
            )
        }
        return null
    }

    private fun extractAmountFromText(text: String): Double {
        val regex = Regex("""(\d+[\d,.]*)?\s*TL""")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
    }

    private fun saveToDatabase(transaction: TransactionEntity) {
        Log.d("KatipApp", "Kâtip Not Aldı -> ${transaction.merchant}: ${transaction.amount} TL (${transaction.source})")
    }
}
