package com.lauren.td

data class TransactionData(
    val reference: String,
    val amount: Double,
    val type: String,
    val otherParty: String?,
    val transactionCost: Double?,
    val time: Long,
    val source: String
)