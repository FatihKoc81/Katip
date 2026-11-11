package com.fatihkooc.katip

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var dialogShowing = false
    private var setupDialog by mutableIntStateOf(0) // 0: yok, 1: gizlilik, 2: bildirim erişimi

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
        setContent {
            KatipApp(resumeTick)
            SetupDialogs()
        }
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
        showOnce { setupDialog = 1 }
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
        showOnce { setupDialog = 2 }
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

    /** İzin açıklama pencereleri: Compose ile çizilir, köşeler 28dp yuvarlak. */
    @Composable
    private fun SetupDialogs() {
        if (setupDialog == 0) return
        val teal = Color(0xFF00897B)
        val tr = Locale("tr", "TR")
        MaterialTheme(colorScheme = lightColorScheme()) {
            if (setupDialog == 1) {
                AlertDialog(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                    shape = RoundedCornerShape(28.dp),
                    containerColor = Color.White,
                    title = { Text("Gizliliğin güvende") },
                    text = {
                        Text(
                            "Kâtip, harcamalarını otomatik takip etmek için banka SMS'lerini, banka ve " +
                                "cüzdan uygulaması bildirimlerini ve e-posta bildirimlerini okur.\n\n" +
                                "• Sadece tutar ve işlem bilgisi alınır; diğer mesajlar yok sayılır.\n" +
                                "• Her şey yalnızca telefonunda işlenir ve saklanır.\n" +
                                "• Hiçbir veri internete gönderilmez, kimseyle paylaşılmaz.\n" +
                                "• Uygulamayı silince tüm kayıtlar da silinir.\n\n" +
                                "Devam edince Android senden birkaç izin isteyecek.",
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            prefs.edit().putBoolean("disclosed_v2", true).apply()
                            setupDialog = 0
                            dialogShowing = false
                            continueSetup()
                        }) { Text("Anladım, devam".uppercase(tr), color = teal) }
                    },
                    dismissButton = {
                        TextButton(onClick = { setupDialog = 0; dialogShowing = false }) {
                            Text("Şimdi değil".uppercase(tr), color = teal)
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { setupDialog = 0; dialogShowing = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = Color.White,
                    title = { Text("Son adım: bildirim erişimi") },
                    text = {
                        Text(
                            "Banka, cüzdan ve e-posta bildirimlerini okuyabilmem için Android'in " +
                                "açacağı sayfada Kâtip'in düğmesini aç. Sadece bir kez yapılır.\n\n" +
                                "Düğme gri ve basılamıyorsa: Ayarlar → Uygulamalar → Kâtip → sağ üst ⋮ → " +
                                "\"Kısıtlı ayarlara izin ver\", sonra buraya dön.",
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            setupDialog = 0
                            dialogShowing = false
                            openNotificationAccessPage()
                        }) { Text("Sayfayı aç".uppercase(tr), color = teal) }
                    },
                    dismissButton = {
                        TextButton(onClick = { setupDialog = 0; dialogShowing = false }) {
                            Text("Sonra".uppercase(tr), color = teal)
                        }
                    }
                )
            }
        }
    }
}
