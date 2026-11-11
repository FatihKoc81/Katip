package com.fatihkooc.katip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// E-posta bildirimi, SMS'ten daha geç gelebilir; çift kaydı önlemek için pencere daha geniş.
private const val MAIL_DUP_WINDOW = 20 * 60 * 1000L

private const val REVIEW_CHANNEL = "katip_transaction_review"

private fun notifyReview(context: Context) {
    try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(REVIEW_CHANNEL, "İşlem onayları", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Kâtip'in tutarını doğrulamanızı istediği mesajlar"
                }
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 4201, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, REVIEW_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Kâtip: Tutarı doğrulayın")
            .setContentText("Bir banka veya fatura mesajındaki tutar için onayınız gerekiyor.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Kâtip bir finans mesajını güvenle ayrıştıramadı. Uygulamayı açıp önerilen tutarı onaylayın veya düzeltin."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(4201, notification)
    } catch (_: SecurityException) {
        // Bildirim izni yoksa bekleyen mesaj uygulama açıldığında yine gösterilir.
    } catch (_: Exception) {
    }
}

private fun save(context: Context, text: String, source: String, fromMail: Boolean = false) {
    val store = Store(context)
    val cleanText = text.trim().take(4000)
    val parsed = BankParser.parse(cleanText, fromMail)
    if (parsed != null) {
        val now = System.currentTimeMillis()
        val txn = Txn(now, parsed.isIncome, parsed.title, parsed.amount, parsed.category, cleanText.take(1000), now, source)
        if (fromMail) store.addAuto(txn, MAIL_DUP_WINDOW) else store.addAuto(txn)
        return
    }

    // Ayrıştırıcı emin değilse tutarı harcama olarak yazma. Kullanıcıya onay kuyruğuna al.
    val suggestion = BankParser.suggestForConfirmation(cleanText) ?: return
    val now = System.currentTimeMillis()
    if (store.addPending(PendingReview(now, cleanText, suggestion, source))) notifyReview(context)
}

class KatipNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (!BankParser.acceptPackage(sbn.packageName)) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (body.isBlank()) return
        save(
            applicationContext, "$title $body", "bildirim",
            fromMail = BankParser.isMailPackage(sbn.packageName)
        )
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs == null || msgs.isEmpty()) return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        if (body.isBlank()) return
        save(context, body, "sms")
    }
}
