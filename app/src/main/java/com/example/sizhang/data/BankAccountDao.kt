package com.example.sizhang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY bank ASC, cardLast4 ASC")
    fun observeAll(): Flow<List<BankAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: BankAccountEntity): Long

    @Query(
        """
        UPDATE bank_accounts
        SET balanceCents = :balanceCents,
            updatedAt = :observedAt,
            dayStartBalanceCents = CASE
                WHEN snapshotEpochDay IS NULL OR snapshotEpochDay != :snapshotEpochDay
                    THEN COALESCE(balanceCents, :balanceCents)
                ELSE dayStartBalanceCents
            END,
            snapshotEpochDay = CASE
                WHEN snapshotEpochDay IS NULL OR snapshotEpochDay != :snapshotEpochDay
                    THEN :snapshotEpochDay
                ELSE snapshotEpochDay
            END
        WHERE accountKey = :accountKey AND :observedAt >= updatedAt
        """,
    )
    suspend fun updateBalanceIfNewer(
        accountKey: String,
        balanceCents: Long,
        observedAt: Long,
        snapshotEpochDay: Long,
    ): Int

    @Query(
        """
        UPDATE bank_accounts
        SET dayStartBalanceCents = balanceCents,
            snapshotEpochDay = :snapshotEpochDay
        WHERE balanceCents IS NOT NULL
          AND (snapshotEpochDay IS NULL OR snapshotEpochDay != :snapshotEpochDay)
        """,
    )
    suspend fun ensureDailySnapshots(snapshotEpochDay: Long)

    @Query(
        """
        UPDATE bank_accounts
        SET dayStartBalanceCents = balanceCents,
            snapshotEpochDay = :snapshotEpochDay
        WHERE balanceCents IS NOT NULL
        """,
    )
    suspend fun refreshDailySnapshots(snapshotEpochDay: Long)
}
