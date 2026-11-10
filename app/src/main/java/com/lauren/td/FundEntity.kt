package com.lauren.td

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "funds")
data class FundEntity(
    @PrimaryKey
    val name: String,
    val targetAmount: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)