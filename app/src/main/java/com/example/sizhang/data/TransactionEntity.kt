package com.example.sizhang.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionKind {
    EXPENSE,
    REFUND,
    INCOME,
}

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val kind: TransactionKind,
    val occurredAt: Long,
    val merchant: String?,
    val cardLast4: String?,
    val bank: String,
    val sender: String,
    val fingerprint: String,
    val currency: String = "CNY",
    val createdAt: Long = System.currentTimeMillis(),
    val isExcluded: Boolean = false,
)

val TransactionEntity.signedExpenseCents: Long
    get() = if (isExcluded || currency != "CNY") {
        0
    } else when (kind) {
        TransactionKind.EXPENSE -> amountCents
        TransactionKind.REFUND -> -amountCents
        TransactionKind.INCOME -> 0
    }
