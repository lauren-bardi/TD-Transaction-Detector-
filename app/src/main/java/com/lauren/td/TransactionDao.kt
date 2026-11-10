package com.lauren.td

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TransactionSummary(
    val grandTotal: Double,
    val totalCount: Int,
    val plannedTotal: Double,
    val plannedCount: Int,
    val unplannedTotal: Double,
    val unplannedCount: Int,
    val unexpectedTotal: Double,
    val unexpectedCount: Int,
    val unknownTotal: Double,
    val unknownCount: Int
)

data class CategorySpend(
    val category: String,
    val total: Double
)

data class FundSpend(
    val fund: String,
    val category: String,
    val total: Double
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY time DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY time DESC")
    fun getAllFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE reference = :reference LIMIT 1")
    suspend fun findByReference(reference: String): TransactionEntity?

    @Query("UPDATE transactions SET fund = :fund WHERE reference = :ref")
    suspend fun setFund(ref: String, fund: String?)

    @Query("SELECT COUNT(*) FROM transactions WHERE fund = :fundName")
    suspend fun countTransactionsForFund(fundName: String): Int

    @Query("""
        SELECT
            COALESCE(SUM(amount), 0) as grandTotal,
            COUNT(*) as totalCount,
            COALESCE(SUM(CASE WHEN intent = 'PLANNED' THEN amount ELSE 0 END), 0) as plannedTotal,
            COALESCE(SUM(CASE WHEN intent = 'PLANNED' THEN 1 ELSE 0 END), 0) as plannedCount,
            COALESCE(SUM(CASE WHEN intent = 'UNPLANNED' THEN amount ELSE 0 END), 0) as unplannedTotal,
            COALESCE(SUM(CASE WHEN intent = 'UNPLANNED' THEN 1 ELSE 0 END), 0) as unplannedCount,
            COALESCE(SUM(CASE WHEN intent = 'UNEXPECTED_BUT_NECESSARY' THEN amount ELSE 0 END), 0) as unexpectedTotal,
            COALESCE(SUM(CASE WHEN intent = 'UNEXPECTED_BUT_NECESSARY' THEN 1 ELSE 0 END), 0) as unexpectedCount,
            COALESCE(SUM(CASE WHEN intent IS NULL THEN amount ELSE 0 END), 0) as unknownTotal,
            COALESCE(SUM(CASE WHEN intent IS NULL THEN 1 ELSE 0 END), 0) as unknownCount
        FROM transactions
        WHERE type IN ('SENT', 'TEST')
          AND time >= :startOfMonth
    """)
    fun getMonthlySummaryFlow(startOfMonth: Long): Flow<TransactionSummary>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM transactions
        WHERE type IN ('SENT', 'TEST')
          AND category IS NOT NULL
          AND time >= :startOfMonth
        GROUP BY category
    """)
    fun getMonthlySpendingByCategory(startOfMonth: Long): Flow<List<CategorySpend>>

    @Query("""
        SELECT fund,
               COALESCE(category, 'Uncategorised') as category,
               SUM(amount) as total
        FROM transactions
        WHERE fund IS NOT NULL
          AND type IN ('SENT', 'TEST')
        GROUP BY fund, COALESCE(category, 'Uncategorised')
        ORDER BY fund, total DESC
    """)
    fun getFundSpendingFlow(): Flow<List<FundSpend>>
}