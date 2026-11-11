package com.fatihkooc.katip

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional, user-enabled reader for İşCep's in-app payment confirmation screen.
 * It inspects text only when İşCep is the foreground package.
 */
class IsCepScreenReaderService : AccessibilityService() {
    private var lastFingerprint: String = ""
    private var lastSeenAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.lowercase().orEmpty()
        if (!pkg.contains("iscep") && !pkg.contains("pozitron")) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString()?.lowercase().orEmpty()
        if (!rootPkg.contains("iscep") && !rootPkg.contains("pozitron")) {
            root.recycle()
            return
        }

        val text = StringBuilder()
        collectText(root, text)
        root.recycle()
        val content = text.toString().trim()
        val normalized = Categories.norm(content)

        // Only record explicit payment-started confirmation screens with a card transaction amount.
        if (!normalized.contains("odeme baslatilmistir") &&
            !normalized.contains("odeme baslatildi")) return
        if (!normalized.contains("kartinizdan")) return

        val parsed = BankParser.parse(content)
        if (parsed == null || parsed.isIncome) return

        val fingerprint = (pkg + "|" + normalized.hashCode() + "|" + parsed.amount).hashCode().toString()
        val now = System.currentTimeMillis()
        if (fingerprint == lastFingerprint && now - lastSeenAt < 120_000L) return
        lastFingerprint = fingerprint
        lastSeenAt = now

        val txn = Txn(
            id = now,
            isIncome = false,
            title = parsed.title,
            amount = parsed.amount,
            category = parsed.category,
            note = content.take(1000),
            date = now,
            source = "ekran"
        )
        Store(applicationContext).addAuto(txn, 5_000L)
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (out.isNotEmpty()) out.append(' ')
            out.append(it)
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (out.isNotEmpty()) out.append(' ')
            out.append(it)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out)
            child.recycle()
        }
    }

    override fun onInterrupt() = Unit
}
