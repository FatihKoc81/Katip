package com.fatihkooc.katip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.IconButton
import android.app.DownloadManager
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF090F1E)
private val Card1 = Color(0xFF121E33)
private val Card2 = Color(0xFF1A2B47)
private val Blue = Color(0xFF568FFF)
private val Green = Color(0xFF42DDB5)
private val Red = Color(0xFFFF7185)
private val Amber = Color(0xFFFFC66D)
private val Muted = Color(0xFFB8C8E3)
private val Edge = Color(0xFF2A3B57)
private val HeroBrush = Brush.linearGradient(listOf(Color(0xFF17345C), Color(0xFF10223F), Color(0xFF0E1A30)))
private val ScreenBrush = Brush.verticalGradient(listOf(Color(0xFF101B30), Color(0xFF090F1E), Color(0xFF0D1729)))
private val Palette = listOf(
    Color(0xFF477BFF), Color(0xFF3DDC97), Color(0xFFFFC66D), Color(0xFFB66DFF), Color(0xFFFF6B6B),
    Color(0xFF4FD1E8), Color(0xFFFF9F5A), Color(0xFF9BE15D), Color(0xFF8A94A6)
)
private val TR = Locale("tr", "TR")

// Güncelleme bağlantıları (depo adı farklıysa yalnızca bunları değiştir)
private const val REPO_URL = "https://github.com/FatihKoc81/Katip"
private const val APK_URL = "$REPO_URL/releases/latest/download/Katip.apk"
private const val RELEASES_URL = "$REPO_URL/releases/latest"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/FatihKoc81/Katip/releases/latest"

private fun appVersion(c: Context): String = try {
    c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
} catch (e: Exception) { "?" }

private fun openLink(c: Context, url: String): Boolean = try {
    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) { false }

private data class UpdateInfo(val versionCode: Long, val apkUrl: String)

private fun checkLatestUpdate(): UpdateInfo? {
    val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 10000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Katip-Android")
    }
    return try {
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val code = json.optString("tag_name").removePrefix("v").toLongOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apk = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == "Katip.apk") {
                apk = asset.optString("browser_download_url")
                break
            }
        }
        if (apk.isBlank()) null else UpdateInfo(code, apk)
    } finally {
        connection.disconnect()
    }
}

private fun downloadUpdate(context: Context, url: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("Kâtip güncellemesi")
        .setDescription("Yeni sürüm indiriliyor. İndirme bitince bildirime dokunup kurulumu onayla.")
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Katip-update.apk")
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun catColor(name: String): Color {
    val i = Categories.expense.indexOf(name)
    return if (i >= 0) Palette[i % Palette.size] else Palette[(name.hashCode() and 0x7fffffff) % Palette.size]
}

fun money(v: Double): String = NumberFormat.getCurrencyInstance(TR).format(v)

private fun fmtInput(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else String.format(TR, "%.2f", d)

private fun sourceTag(s: String): String = when (s) {
    "bildirim" -> " • Bildirim"
    "sms" -> " • SMS"
    "deneme" -> " • Deneme"
    else -> ""
}

private fun notifAccess(c: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

private fun smsGranted(c: Context): Boolean =
    ContextCompat.checkSelfPermission(c, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

data class Period(val start: Long, val end: Long, val label: String)

private fun period(mode: Int, offset: Int): Period {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    val e: Calendar
    val label: String
    when (mode) {
        0 -> {
            c.add(Calendar.DAY_OF_YEAR, offset)
            e = c.clone() as Calendar
            e.add(Calendar.DAY_OF_YEAR, 1)
            label = SimpleDateFormat("dd MMMM yyyy", TR).format(c.time)
        }
        1 -> {
            c.firstDayOfWeek = Calendar.MONDAY
            c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            c.add(Calendar.WEEK_OF_YEAR, offset)
            e = c.clone() as Calendar
            e.add(Calendar.DAY_OF_YEAR, 7)
            val last = e.clone() as Calendar
            last.add(Calendar.DAY_OF_YEAR, -1)
            label = SimpleDateFormat("dd MMM", TR).format(c.time) + " – " + SimpleDateFormat("dd MMM yyyy", TR).format(last.time)
        }
        else -> {
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, offset)
            e = c.clone() as Calendar
            e.add(Calendar.MONTH, 1)
            label = SimpleDateFormat("MMMM yyyy", TR).format(c.time)
        }
    }
    return Period(c.timeInMillis, e.timeInMillis, label)
}

private fun List<Txn>.expenseByCategory(): List<Pair<String, Double>> =
    this.filterNot { it.isIncome }
        .groupBy { it.category }
        .map { it.key to it.value.sumOf { x -> x.amount } }
        .sortedByDescending { it.second }

// ---------------------------------------------------------------- ROOT

@Composable
fun KatipApp(resumeTick: Int) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var txns by remember { mutableStateOf(store.load()) }
    var pendingReviews by remember { mutableStateOf(store.pending()) }
    var pendingToEdit by remember { mutableStateOf<PendingReview?>(null) }
    var budget by remember { mutableDoubleStateOf(store.budget()) }
    var localTick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    val updateScope = rememberCoroutineScope()
    val tick = resumeTick + localTick

    DisposableEffect(Unit) {
        val l = store.listen { txns = store.load(); pendingReviews = store.pending() }
        onDispose { store.unlisten(l) }
    }

    val autoOk = remember(tick) { notifAccess(ctx) || smsGranted(ctx) }
    val tabs = listOf("Ana Sayfa" to "⌂", "İşlemler" to "≡", "Raporlar" to "◔", "Ayarlar" to "⚙")

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, background = Navy, surface = Card1,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0B1426),
                    contentColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted,
                        indicatorColor = Color.Transparent
                    )
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Text("⌂", fontSize = 21.sp, color = if (tab == 0) Color.White else Muted) },
                        label = { Text("Ana Sayfa", fontSize = 9.sp) }, colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Text("⇄", fontSize = 21.sp, color = if (tab == 1) Color.White else Muted) },
                        label = { Text("İşlemler", fontSize = 9.sp) }, colors = navColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { editing = null; pendingToEdit = null; showDialog = true },
                        icon = {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) {
                                Text("+", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Medium)
                            }
                        },
                        label = { Text("Ekle", fontSize = 9.sp, color = Muted) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Text("◷", fontSize = 21.sp, color = if (tab == 2) Color.White else Muted) },
                        label = { Text("Raporlar", fontSize = 9.sp) }, colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == 3, onClick = { tab = 3 },
                        icon = { Text("⚙", fontSize = 20.sp, color = if (tab == 3) Color.White else Muted) },
                        label = { Text("Ayarlar", fontSize = 9.sp) }, colors = navColors
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(ScreenBrush)) {
                when (tab) {
                    0 -> HomeScreen(
                        txns, budget, autoOk,
                        updateBusy = updateBusy,
                        updateMessage = updateMessage,
                        onCheckUpdate = {
                            if (!updateBusy) {
                                updateBusy = true
                                updateMessage = "Güncelleme kontrol ediliyor…"
                                updateScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { checkLatestUpdate() }.getOrNull()
                                    }
                                    updateBusy = false
                                    if (result == null) {
                                        updateMessage = "Güncelleme kontrol edilemedi. İnternet bağlantını kontrol et."
                                    } else {
                                        val installed = try {
                                            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                                            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                                        } catch (_: Exception) { 0L }
                                        if (result.versionCode > installed) {
                                            downloadUpdate(ctx, result.apkUrl)
                                            updateMessage = "Yeni sürüm bulundu ve indiriliyor. İndirme bildirimi geldiğinde dokunup kurulumu onayla."
                                        } else {
                                            updateMessage = "Kâtip güncel. Yeni sürüm bulunamadı."
                                        }
                                    }
                                }
                            }
                        },
                        onSettings = { tab = 3 },
                        onSeeAll = { tab = 1 },
                        onEdit = { editing = it; pendingToEdit = null; showDialog = true }
                    )
                    1 -> TxnScreen(txns) { editing = it; pendingToEdit = null; showDialog = true }
                    2 -> ReportScreen(txns)
                    else -> SettingsScreen(
                        store, budget, tick,
                        onBump = { localTick++ },
                        onBudget = { store.setBudget(it); budget = it }
                    )
                }
            }
        }

        if (showDialog) {
            TxnDialog(
                initial = editing ?: pendingToEdit?.asTxn(),
                onSave = { t ->
                    val review = pendingToEdit
                    if (review != null) {
                        store.add(t.copy(id = System.currentTimeMillis(), note = review.text, source = review.source))
                        store.removePending(review.id)
                        pendingToEdit = null
                    } else if (editing == null) store.add(t) else store.update(t)
                    showDialog = false
                },
                onDelete = editing?.let { e -> { store.delete(e.id); showDialog = false } },
                onDismiss = { showDialog = false; pendingToEdit = null }
            )
        }

        if (!showDialog && pendingReviews.isNotEmpty()) {
            val review = pendingReviews.first()
            PendingReviewDialog(
                item = review,
                onConfirm = { amount ->
                    val base = review.asTxn()
                    store.add(base.copy(id = System.currentTimeMillis(), amount = amount))
                    store.removePending(review.id)
                },
                onEdit = {
                    editing = null
                    pendingToEdit = review
                    showDialog = true
                },
                onSkip = { store.removePending(review.id) }
            )
        }
    }
}

// ---------------------------------------------------------------- COMPONENTS

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
private fun Donut(slices: List<Pair<String, Double>>, modifier: Modifier = Modifier.size(120.dp)) {
    val total = slices.sumOf { it.second }
    Canvas(modifier) {
        val stroke = 22.dp.toPx()
        val tl = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        if (total <= 0.0) {
            drawArc(Color(0x33FFFFFF), 0f, 360f, false, tl, sz, style = Stroke(stroke))
        } else {
            var start = -90f
            slices.forEach { s ->
                val sweep = (s.second / total * 360.0).toFloat()
                drawArc(catColor(s.first), start, sweep, false, tl, sz, style = Stroke(stroke))
                start += sweep
            }
        }
    }
}

@Composable
private fun CategoryBlock(rows: List<Pair<String, Double>>, compact: Boolean = false) {
    val total = rows.sumOf { it.second }
    Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Edge), colors = CardDefaults.cardColors(containerColor = Card1)) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 14.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Harcama dağılımı", fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 16.sp)
                if (compact) Text("KATEGORİLER", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            if (rows.isEmpty()) Text("Bu dönemde harcama yok.", color = Muted, fontSize = 13.sp)
            else if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(rows, Modifier.size(132.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        rows.take(5).forEach { (cat, value) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(catColor(cat)))
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(money(value), fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("%" + (value / total * 100).toInt(), fontSize = 11.sp, color = Muted)
                            }
                        }
                        if (rows.size > 5) Text("+" + (rows.size - 5) + " kategori daha", color = Muted, fontSize = 10.sp)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(rows)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.take(5).forEach { (cat, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(catColor(cat)))
                                Spacer(Modifier.width(8.dp))
                                Text(cat, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(money(v), fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1.25f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                rows.forEach { (cat, v) ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, fontSize = 13.sp)
                            Text("%" + (if (total > 0) (v / total * 100).toInt() else 0), fontSize = 13.sp, color = Muted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Bar(if (total > 0) (v / total).toFloat() else 0f, catColor(cat))
                    }
                }
            }
        }
    }
}
@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Edge), colors = CardDefaults.cardColors(containerColor = Card1)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontSize = if (value.length > 11) 12.sp else 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TxnRow(t: Txn, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", TR) }
    Card(
        border = BorderStroke(1.dp, Edge),
        colors = CardDefaults.cardColors(containerColor = Card1),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (t.isIncome) Green else catColor(t.category)
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) { Text(t.category.take(1), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.category + " • " + fmt.format(Date(t.date)) + sourceTag(t.source), color = Muted, fontSize = 12.sp)
            }
            Text(
                (if (t.isIncome) "+" else "-") + money(t.amount),
                color = if (t.isIncome) Green else Red,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 112.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- SCREENS

@Composable
private fun HomeScreen(
    txns: List<Txn>, budget: Double, autoOk: Boolean,
    updateBusy: Boolean, updateMessage: String, onCheckUpdate: () -> Unit,
    onSettings: () -> Unit, onSeeAll: () -> Unit, onEdit: (Txn) -> Unit
) {
    val p = remember(txns.size) { period(2, 0) }
    val month = txns.filter { it.date >= p.start && it.date < p.end }
    val inc = month.filter { it.isIncome }.sumOf { it.amount }
    val exp = month.filterNot { it.isIncome }.sumOf { it.amount }
    val balance = txns.sumOf { if (it.isIncome) it.amount else -it.amount }
    val remaining = budget - exp
    val ratio = if (budget > 0) (exp / budget).toFloat().coerceIn(0f, 1f) else 0f

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 7.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("K", fontSize = 31.sp, fontWeight = FontWeight.Light, color = Color(0xFFB8D8FF))
                    Column(verticalArrangement = Arrangement.spacedBy((-3).dp)) {
                        Text("KÂTİP", fontWeight = FontWeight.SemiBold, letterSpacing = 2.3.sp, fontSize = 18.sp)
                        Text("Paranın kontrolü sende.", fontSize = 10.sp, color = Muted)
                    }
                }
                IconButton(onClick = onCheckUpdate, modifier = Modifier.size(38.dp), enabled = !updateBusy) {
                    Text(if (updateBusy) "…" else "⋯", color = Muted, fontSize = 24.sp)
                }
            }
        }
        if (updateMessage.isNotBlank()) item { Text(updateMessage, color = Muted, fontSize = 11.sp) }
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFF30496B)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF142541))
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1.1f)) {
                            Text("TOPLAM BAKİYE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(money(balance), fontSize = if (money(balance).length > 13) 22.sp else 27.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text(p.label, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Column(Modifier.weight(0.9f)) {
                            Text("AYLIK BÜTÇE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(if (budget > 0) money(budget) else "Belirlenmedi", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text(if (budget > 0) "Kalan " + money(remaining) else "Ayarlar bölümünden belirle", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF34445E))) {
                        Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF4A9DFF), Color(0xFF54D5D0)))))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gelir  " + money(inc), color = Green, fontSize = 10.sp)
                        Text("Gider  " + money(exp), color = Red, fontSize = 10.sp)
                    }
                }
            }
        }
        if (!autoOk) {
            item {
                Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF33291F))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Otomatik takip kapalı", fontWeight = FontWeight.Bold, color = Amber, fontSize = 12.sp)
                            Text("Banka bildirimleri için izin ver.", fontSize = 10.sp, color = Muted)
                        }
                        TextButton(onClick = onSettings) { Text("Aç", fontSize = 12.sp) }
                    }
                }
            }
        }
        item { CategoryBlock(month.expenseByCategory(), compact = true) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Son işlemler", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("En son hareketlerin", fontSize = 10.sp, color = Muted)
                }
                TextButton(onClick = onSeeAll) { Text("Tümünü gör", fontSize = 11.sp) }
            }
        }
        if (txns.isEmpty()) item { Text("Henüz işlem yok. Ortadaki + düğmesiyle ekleyebilirsin.", color = Muted, fontSize = 12.sp) }
        else items(txns.take(5), key = { it.id }) { t -> TxnRow(t) { onEdit(t) } }
    }
}
@Composable
private fun TxnScreen(txns: List<Txn>, onEdit: (Txn) -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    val list = when (filter) {
        1 -> txns.filter { it.isIncome }
        2 -> txns.filterNot { it.isIncome }
        else -> txns
    }
    val dayFmt = remember { SimpleDateFormat("dd MMMM yyyy, EEEE", TR) }
    val grouped = list.groupBy { dayFmt.format(Date(it.date)) }

    Column(Modifier.fillMaxSize()) {
        Text("İşlemler", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tümü", "Gelir", "Gider").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isEmpty()) {
                item { Text("Kayıt yok.", color = Muted) }
            }
            for ((day, rows) in grouped) {
                item {
                    val dayExp = rows.filterNot { it.isIncome }.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(day, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (dayExp > 0) Text("-" + money(dayExp), color = Red, fontSize = 12.sp)
                    }
                }
                items(rows, key = { it.id }) { t -> TxnRow(t) { onEdit(t) } }
            }
        }
    }
}

@Composable
private fun ReportScreen(txns: List<Txn>) {
    var mode by remember { mutableIntStateOf(2) }
    var offset by remember { mutableIntStateOf(0) }
    val p = remember(mode, offset) { period(mode, offset) }
    val inRange = txns.filter { it.date >= p.start && it.date < p.end }
    val inc = inRange.filter { it.isIncome }.sumOf { it.amount }
    val exp = inRange.filterNot { it.isIncome }.sumOf { it.amount }
    val rows = inRange.expenseByCategory()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Raporlar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Harcama özetlerin", fontSize = 12.sp, color = Muted)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Günlük", "Haftalık", "Aylık").forEachIndexed { i, label ->
                    FilterChip(selected = mode == i, onClick = { mode = i; offset = 0 }, label = { Text(label) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { offset-- }) { Text("‹", fontSize = 24.sp) }
                Text(p.label, fontWeight = FontWeight.Bold)
                TextButton(onClick = { offset++ }, enabled = offset < 0) { Text("›", fontSize = 24.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("GELİR", money(inc), Green, Modifier.weight(1f))
                StatCard("GİDER", money(exp), Red, Modifier.weight(1f))
                StatCard("KALAN", money(inc - exp), if (inc - exp >= 0) Amber else Red, Modifier.weight(1f))
            }
        }
        item { CategoryBlock(rows) }
        item {
            val biggest = inRange.filterNot { it.isIncome }.maxByOrNull { it.amount }
            if (biggest != null) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("En yüksek harcama", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(biggest.title + " • " + money(biggest.amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    store: Store, budget: Double, tick: Int,
    onBump: () -> Unit, onBudget: (Double) -> Unit
) {
    val ctx = LocalContext.current
    val notifOk = remember(tick) { notifAccess(ctx) }
    val smsOk = remember(tick) { smsGranted(ctx) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onBump() }
    var checkResult by remember { mutableStateOf("") }
    var budgetText by remember { mutableStateOf(if (budget > 0) fmtInput(budget) else "") }
    var budgetMsg by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Ayarlar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Otomatik takip izinleri", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Bildirim erişimi: " + if (notifOk) "AÇIK ✓" else "KAPALI", color = if (notifOk) Green else Red)
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Bildirim erişimini aç") }
                    Text("SMS izni: " + if (smsOk) "VERİLDİ ✓" else "YOK", color = if (smsOk) Green else Red)
                    Button(onClick = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) }) { Text("SMS izni ver") }
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Uygulama ayarlarını aç") }
                    Text(
                        "Not: APK'yı kendin yüklediysen Android izinleri engelleyebilir. Uygulama ayarları → sağ üst ⋮ → " +
                            "\"Kısıtlı ayarlara izin ver\" seçeneğini aç, sonra izinleri tekrar dene.",
                        fontSize = 12.sp, color = Muted
                    )
                    Button(onClick = {
                        val n = notifAccess(ctx)
                        val s = smsGranted(ctx)
                        checkResult = when {
                            n && s -> "Tüm izinler tamam. Otomatik takip aktif."
                            n -> "Bildirim erişimi açık. SMS izni eksik."
                            s -> "SMS izni var. Bildirim erişimi eksik."
                            else -> "İki izin de eksik. Yukarıdaki butonlarla aç."
                        }
                        onBump()
                    }) { Text("İzinleri kontrol et") }
                    if (checkResult.isNotBlank()) Text(checkResult, fontSize = 13.sp)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aylık bütçe", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Aylık harcama limiti (₺)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Button(onClick = {
                        val v = if (budgetText.isBlank()) 0.0 else BankParser.toDouble(budgetText)
                        if (v == null || v < 0) budgetMsg = "Geçerli bir tutar gir."
                        else { onBudget(v); budgetMsg = if (v == 0.0) "Bütçe kapatıldı." else "Bütçe kaydedildi." }
                    }) { Text("Bütçeyi kaydet") }
                    if (budgetMsg.isNotBlank()) Text(budgetMsg, fontSize = 13.sp, color = Muted)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mesaj deneme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Bir banka SMS'ini yapıştır; Kâtip tutarı ve kategoriyi bulup ekler.", fontSize = 12.sp, color = Muted)
                    OutlinedTextField(value = sample, onValueChange = { sample = it }, minLines = 3, label = { Text("SMS / bildirim metni") })
                    Button(onClick = {
                        val r = BankParser.parse(sample)
                        val suggestion = r?.amount ?: BankParser.suggestForConfirmation(sample)
                        if (suggestion == null) sampleResult = "Bu metinde doğrulanabilecek bir tutar bulunamadı."
                        else {
                            val now = System.currentTimeMillis()
                            store.addPending(PendingReview(now, sample.trim().take(4000), suggestion, "deneme"))
                            sampleResult = "Tutar onayınıza sunuldu. Kaydetmek için açılan soruyu yanıtlayın."
                            sample = ""
                        }
                    }) { Text("Ayrıştır ve doğrula") }
                    if (sampleResult.isNotBlank()) Text(sampleResult, fontSize = 13.sp)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Güncelleme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Yüklü sürüm: " + appVersion(ctx), fontSize = 13.sp, color = Muted)
                    Text(
                        "Yeni sürüm GitHub'daki Releases sayfasında yayınlanır. İndirdikten sonra APK'yı açıp yükle; verilerin korunur.",
                        fontSize = 12.sp, color = Muted
                    )
                    Button(onClick = {
                        updateMsg = if (openLink(ctx, APK_URL)) "" else "Tarayıcı açılamadı."
                    }) { Text("Son sürümü indir") }
                    OutlinedButton(onClick = {
                        updateMsg = if (openLink(ctx, RELEASES_URL)) "" else "Tarayıcı açılamadı."
                    }) { Text("Sürümler sayfasını aç") }
                    if (updateMsg.isNotBlank()) Text(updateMsg, fontSize = 13.sp, color = Red)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Veriler", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Tüm kayıtlar yalnızca bu telefonda saklanır. İnternete gönderilmez.", fontSize = 12.sp, color = Muted)
                    OutlinedButton(onClick = { confirmClear = true }) { Text("Tüm verileri sil", color = Red) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Tüm veriler silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { store.clear(); confirmClear = false }) { Text("Sil", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("İptal") } }
        )
    }
}

// ---------------------------------------------------------------- DIALOG

@Composable
private fun PendingReviewDialog(
    item: PendingReview,
    onConfirm: (Double) -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit
) {
    var amount by remember(item.id) { mutableStateOf(fmtInput(item.suggestedAmount)) }
    var error by remember(item.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("İşlemi doğrulayalım") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Bu mesajda şu tutarı buldum. Harcama/gelir tutarı bu mu?", color = Muted)
                Card(
                    border = BorderStroke(1.dp, Edge),
                    colors = CardDefaults.cardColors(containerColor = Card1)
                ) {
                    Text(item.text, Modifier.fillMaxWidth().padding(12.dp), fontSize = 12.sp, color = Muted)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; error = "" },
                    label = { Text("Bulduğum tutar (₺)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp)
                Text("Yanlışsa “Düzelt” seçeneğiyle tutarı ve kategoriyi değiştirebilirsiniz.", fontSize = 12.sp, color = Muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = BankParser.toDouble(amount)
                if (value == null || value <= 0.0) error = "Geçerli bir tutar girin."
                else onConfirm(value)
            }) { Text("Evet, kaydet") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("Atla") }
                TextButton(onClick = onEdit) { Text("Değil, düzelt") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxnDialog(initial: Txn?, onSave: (Txn) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var income by remember { mutableStateOf(initial?.isIncome ?: false) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(if (initial != null) fmtInput(initial.amount) else "") }
    var category by remember { mutableStateOf(initial?.category ?: "Market") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val cats = if (income) Categories.income else Categories.expense

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni işlem" else "İşlemi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !income, onClick = { income = false; category = Categories.expense[0] }, label = { Text("Gider") })
                    FilterChip(selected = income, onClick = { income = true; category = Categories.income[0] }, label = { Text("Gelir") })
                }
                OutlinedTextField(value = title, onValueChange = { title = it; error = "" }, label = { Text("Ad") }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; error = "" },
                    label = { Text("Tutar (₺)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true, label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        cats.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not (isteğe bağlı)") }, minLines = 2)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Bu işlemi sil", color = Red) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = BankParser.toDouble(amount)
                if (title.isBlank()) error = "Adı yaz."
                else if (v == null || v <= 0.0) error = "Geçerli bir tutar gir."
                else {
                    val now = System.currentTimeMillis()
                    onSave(Txn(initial?.id ?: now, income, title.trim(), v, category, note.trim(), initial?.date ?: now, initial?.source ?: "manuel"))
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
