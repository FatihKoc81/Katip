package com.fatihkooc.katip.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "katip_transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val merchant: String,       // Harcama yapılan yer
    val amount: Double,         // Tutar
    val category: String,       // Kategori
    val date: Long,             // Zaman damgası
    val source: String          // Kaynak (Bildirim / SMS)
)
