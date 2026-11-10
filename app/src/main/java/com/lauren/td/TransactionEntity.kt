package com.lauren.td

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val reference: String,
    val amount: Double,
    val type: String,
    val otherParty: String?,
    val transactionCost: Double?,
    val time: Long,
    val source: String,
    val category: String? = null,
    val intent: String? = null,
    val status: String = "AWAITING_CATEGORY",
    val fund: String? = null
)