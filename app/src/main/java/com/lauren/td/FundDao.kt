package com.lauren.td

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FundDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(fund: FundEntity)

    @Delete
    suspend fun delete(fund: FundEntity)

    @Query("SELECT * FROM funds ORDER BY createdAt ASC")
    fun getAllFlow(): Flow<List<FundEntity>>

    @Query("SELECT * FROM funds ORDER BY createdAt ASC")
    suspend fun getAll(): List<FundEntity>

    @Query("SELECT * FROM funds WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FundEntity?

    @Query("UPDATE funds SET isActive = CASE WHEN name = :name THEN 1 ELSE 0 END")
    suspend fun setActive(name: String)

    @Query("UPDATE funds SET isActive = 0")
    suspend fun clearActive()

    @Query("SELECT * FROM funds WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): FundEntity?

    @Query("SELECT * FROM funds WHERE isActive = 1 LIMIT 1")
    fun getActiveFlow(): Flow<FundEntity?>
}