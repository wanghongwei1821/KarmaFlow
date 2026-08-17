package com.example.sizhang.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bank_accounts",
    indices = [Index(value = ["bank"])],
)
data class BankAccountEntity(
    @PrimaryKey val accountKey: String,
    val bank: String,
    val cardLast4: String,
    val balanceCents: Long? = null,
    val updatedAt: Long = 0,
    val dayStartBalanceCents: Long? = null,
    val snapshotEpochDay: Long? = null,
)

fun bankAccountKey(bank: String, cardLast4: String): String = "$bank:$cardLast4"

const val UNKNOWN_CARD_LAST4 = "unknown"
