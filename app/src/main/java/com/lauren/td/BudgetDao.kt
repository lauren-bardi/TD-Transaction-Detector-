package com.lauren.td

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE category = :category LIMIT 1")
    suspend fun findByCategory(category: String): BudgetEntity?

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("SELECT * FROM budgets")
    fun getAllFlow(): Flow<List<BudgetEntity>>
}