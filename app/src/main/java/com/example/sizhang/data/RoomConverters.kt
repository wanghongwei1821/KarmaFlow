package com.example.sizhang.data

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun fromTransactionKind(value: TransactionKind): String = value.name

    @TypeConverter
    fun toTransactionKind(value: String): TransactionKind = TransactionKind.valueOf(value)
}

