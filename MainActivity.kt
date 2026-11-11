package com.fatihkooc.katip

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val smsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            resumeTick++
            askNotificationAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KatipApp(resumeTick) }
        firstRunSetup()
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun smsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notifGranted() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    /** İlk açılışta önce gizlilik açıklaması, sonra izinler; Ayarlar'a girmek gerekmez. */
    private fun firstRunSetup() {
        val prefs = getSharedPreferences("katip_setup", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_v1", false)) return
        prefs.edit().putBoolean("asked_v1", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Gizliliğin güvende")
            .setMessage(
                "Kâtip, harcamalarını otomatik takip etmek için banka SMS'lerini ve " +
                    "banka bildirimlerini okur.\n\n" +
                    "• Sadece tutar ve işlem bilgisi alınır.\n" +
                    "• Her şey yalnızca telefonunda işlenir ve saklanır.\n" +
                    "• Hiçbir veri internete gönderilmez, kimseyle paylaşılmaz.\n" +
                    "• Uygulamayı silince tüm kayıtlar da silinir.\n\n" +
                    "Devam edince Android senden iki izin isteyecek."
            )
            .setCancelable(false)
            .setPositiveButton("Anladım, devam") { _, _ ->
                if (!smsGranted()) {
                    smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                } else {
                    askNotificationAccess()
                }
            }
            .setNegativeButton("Şimdi değil", null)
            .show()
    }

    private fun askNotificationAccess() {
        if (notifGranted() || isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("Bildirim erişimi")
            .setMessage(
                "Banka uygulaması bildirimlerini okuyabilmem için bildirim erişimini " +
                    "açman gerekiyor. Açılan listeden Kâtip'i seçip aç."
            )
            .setPositiveButton("Aç") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Sonra", null)
            .show()
    }
}
