package com.fatihkooc.katip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fatihkooc.katip.data.TransactionEntity

class KatipNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "katip_background_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kâtip Arka Plan Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Harcamaları yakalamak için arka planda sessizce çalışır."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kâtip Görevde 🎙️")
            .setContentText("Banka bildirimleri ve SMS'ler sessizce takip ediliyor.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        // Android'in servisi öldürmesini engelleyen kritik ön plan başlatması
        try {
            startForeground(1001, notification)
        } catch (e: Exception) {
            Log.e("KatipApp", "Foreground başlatılamadı: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""

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
