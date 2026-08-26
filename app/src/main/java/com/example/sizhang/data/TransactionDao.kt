package com.example.sizhang.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET occurredAt = :occurredAt WHERE fingerprint = :fingerprint AND occurredAt != :occurredAt")
    suspend fun updateOccurredAtByFingerprint(fingerprint: String, occurredAt: Long): Int

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isExcluded = :excluded WHERE id = :id")
    suspend fun setExcluded(id: Long, excluded: Boolean)
}
