package com.fatihkooc.katip

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// E-posta bildirimi, SMS'ten daha geç gelebilir; çift kaydı önlemek için pencere daha geniş.
private const val MAIL_DUP_WINDOW = 20 * 60 * 1000L

private fun save(context: Context, text: String, source: String, fromMail: Boolean = false) {
    val p = BankParser.parse(text, fromMail) ?: return
    val now = System.currentTimeMillis()
    val txn = Txn(now, p.isIncome, p.title, p.amount, p.category, text.trim().take(1000), now, source)
    if (fromMail) Store(context).addAuto(txn, MAIL_DUP_WINDOW) else Store(context).addAuto(txn)
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
