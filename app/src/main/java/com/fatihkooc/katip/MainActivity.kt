package com.fatihkooc.katip

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Expense(
    val id: Long, val title: String, val amount: Double,
    val category: String, val note: String, val date: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KatipApp(this) }
    }
}

private fun loadExpenses(context: Context): List<Expense> {
    val raw = context.getSharedPreferences("katip_local", Context.MODE_PRIVATE)
        .getString("expenses", "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Expense(o.getLong("id"), o.getString("title"), o.getDouble("amount"),
                o.getString("category"), o.optString("note"), o.getLong("date"))
        }.sortedByDescending { it.date }
    } catch (_: Exception) { emptyList() }
}

private fun saveExpenses(context: Context, expenses: List<Expense>) {
    val array = JSONArray()
    expenses.forEach { e ->
        array.put(JSONObject().apply {
            put("id", e.id); put("title", e.title); put("amount", e.amount)
            put("category", e.category); put("note", e.note); put("date", e.date)
        })
    }
    context.getSharedPreferences("katip_local", Context.MODE_PRIVATE)
        .edit().putString("expenses", array.toString()).apply()
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KatipApp(context: Context) {
    val navy = Color(0xFF101A30)
    val blue = Color(0xFF477BFF)
    var expenses by remember { mutableStateOf(loadExpenses(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Market") }
    var note by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val categories = listOf("Market", "Yemek", "Ulaşım", "Yakıt", "Fatura", "Alışveriş", "Sağlık", "Eğlence", "Diğer")
    val total = expenses.sumOf { it.amount }
    val monthTotal = expenses.filter {
        val now = java.util.Calendar.getInstance()
        val d = java.util.Calendar.getInstance().apply { timeInMillis = it.date }
        now.get(java.util.Calendar.YEAR) == d.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.MONTH) == d.get(java.util.Calendar.MONTH)
    }.sumOf { it.amount }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = blue, background = navy, surface = Color(0xFF192641),
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = navy,
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("KÂTİP", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("Kişisel harcama defterin", fontSize = 12.sp, color = Color.LightGray)
                    } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = navy)
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { error = ""; showAdd = true }, containerColor = blue) {
                    Text("+", fontSize = 30.sp, color = Color.White)
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF20365C))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("BU AY TOPLAM GİDER", color = Color(0xFFB9C8E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(money(monthTotal), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Tüm kayıtlar", color = Color.LightGray, fontSize = 12.sp); Text(money(total), fontWeight = FontWeight.Bold) }
                                Column(horizontalAlignment = Alignment.End) { Text("İşlem sayısı", color = Color.LightGray, fontSize = 12.sp); Text(expenses.size.toString(), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Harcamalarım", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { error = ""; showAdd = true }) { Text("+ Ekle") }
                    }
                }
                if (expenses.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF192641)), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Henüz harcama yok", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("İlk kaydını eklemek için + düğmesine dokun.", color = Color.LightGray)
                            }
                        }
                    }
                } else {
                    items(expenses, key = { it.id }) { expense ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF192641)), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(expense.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(expense.category + " • " + SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(expense.date)), color = Color.LightGray, fontSize = 12.sp)
                                    if (expense.note.isNotBlank()) Text(expense.note, color = Color(0xFFB9C8E5), fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(money(expense.amount), color = Color(0xFFFFC66D), fontWeight = FontWeight.Bold)
                                    TextButton(onClick = {
                                        expenses = expenses.filterNot { it.id == expense.id }
                                        saveExpenses(context, expenses)
                                    }) { Text("Sil", color = Color(0xFFFF8A8A)) }
                                }
                            }
                        }
                    }
                }
                item { Text("Kayıtlar yalnızca bu telefonda saklanır.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 72.dp)) }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Yeni harcama") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = title, onValueChange = { title = it; error = "" }, label = { Text("Harcama adı") }, singleLine = true)
                        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; error = "" }, label = { Text("Tutar (₺)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                            OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Kategori") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                                categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; categoryExpanded = false }) }
                            }
                        }
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not (isteğe bağlı)") }, minLines = 2)
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val normalized = amount.trim().replace(".", "").replace(",", ".")
                        val value = normalized.toDoubleOrNull()
                        if (title.isBlank()) error = "Harcama adını yaz."
                        else if (value == null || value <= 0.0) error = "Geçerli bir tutar gir."
                        else {
                            expenses = (expenses + Expense(System.currentTimeMillis(), title.trim(), value, category, note.trim(), System.currentTimeMillis())).sortedByDescending { it.date }
                            saveExpenses(context, expenses)
                            title = ""; amount = ""; note = ""; category = "Market"; showAdd = false; error = ""
                        }
                    }) { Text("Kaydet") }
                },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("İptal") } }
            )
        }
    }
}
