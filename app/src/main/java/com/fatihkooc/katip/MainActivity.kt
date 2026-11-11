package com.fatihkooc.katip

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var dialogShowing = false

    private val smsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            resumeTick++
            askBattery()
        }

    private val batteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            resumeTick++
            askNotificationAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KatipApp(resumeTick) }
        setup()
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private val prefs get() = getSharedPreferences("katip_setup", Context.MODE_PRIVATE)

    private fun smsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notifGranted() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun batteryIgnored(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)

    /** Pil izni bir kez sorulur; reddedilirse her açılışta rahatsız edilmez. */
    private fun batteryDone() = batteryIgnored() || prefs.getBoolean("battery_asked", false)

    /**
     * Her açılışta, izinler tamamlanana kadar kullanıcıyı yönlendirir.
     * İlk açılışta önce gizlilik açıklaması gösterilir.
     * Sıra: gizlilik → SMS izni → pil izni → bildirim erişimi.
     */
    private fun setup() {
        if (smsGranted() && notifGranted() && batteryDone()) return
        if (prefs.getBoolean("disclosed_v2", false)) {
            continueSetup()
            return
        }
        showOnce {
            AlertDialog.Builder(this)
                .setTitle("Gizliliğin güvende")
                .setMessage(
                    "Kâtip, harcamalarını otomatik takip etmek için banka SMS'lerini, banka ve " +
                        "cüzdan uygulaması bildirimlerini ve e-posta bildirimlerini okur.\n\n" +
                        "• Sadece tutar ve işlem bilgisi alınır; diğer mesajlar yok sayılır.\n" +
                        "• Her şey yalnızca telefonunda işlenir ve saklanır.\n" +
                        "• Hiçbir veri internete gönderilmez, kimseyle paylaşılmaz.\n" +
                        "• Uygulamayı silince tüm kayıtlar da silinir.\n\n" +
                        "Devam edince Android senden birkaç izin isteyecek."
                )
                .setCancelable(false)
                .setPositiveButton("Anladım, devam") { _, _ ->
                    prefs.edit().putBoolean("disclosed_v2", true).apply()
                    dialogShowing = false
                    continueSetup()
                }
                .setNegativeButton("Şimdi değil") { _, _ -> dialogShowing = false }
                .show()
        }
    }

    private fun continueSetup() {
        if (!smsGranted()) {
            smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
        } else {
            askBattery()
        }
    }

    /** Android'in "arka planda çalışmasına izin ver" penceresini açar; ayarlarda gezmek gerekmez. */
    private fun askBattery() {
        if (batteryDone() || isFinishing) {
            askNotificationAccess()
            return
        }
        prefs.edit().putBoolean("battery_asked", true).apply()
        try {
            batteryLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            askNotificationAccess()
        }
    }

    private fun askNotificationAccess() {
        if (notifGranted() || isFinishing) return
        showOnce {
            AlertDialog.Builder(this)
                .setTitle("Son adım: bildirim erişimi")
                .setMessage(
                    "Banka, cüzdan ve e-posta bildirimlerini okuyabilmem için Android'in " +
                        "açacağı sayfada Kâtip'in düğmesini aç. Sadece bir kez yapılır.\n\n" +
                        "Düğme gri ve basılamıyorsa: Ayarlar → Uygulamalar → Kâtip → sağ üst ⋮ → " +
                        "\"Kısıtlı ayarlara izin ver\", sonra buraya dön."
                )
                .setPositiveButton("Sayfayı aç") { _, _ ->
                    dialogShowing = false
                    openNotificationAccessPage()
                }
                .setNegativeButton("Sonra", null)
                .setOnDismissListener { dialogShowing = false }
                .show()
        }
    }

    /** Android 11+ ta doğrudan Kâtip'in kendi sayfasını açar; olmazsa genel listeyi. */
    private fun openNotificationAccessPage() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val cn = ComponentName(this, KatipNotificationListener::class.java)
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
                )
                return
            }
        } catch (_: Exception) {
        }
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun showOnce(block: () -> Unit) {
        if (dialogShowing) return
        dialogShowing = true
        block()
    }
}
