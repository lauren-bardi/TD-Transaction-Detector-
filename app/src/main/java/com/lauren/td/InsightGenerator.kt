package com.lauren.td

/**
 * Produces a short, situational sentence from the current month's spending
 * and the user's budgets. Deliberately non-AI: it is template-based
 * generation that picks ONE observation per call.
 */
object InsightGenerator {

    fun generate(
        summary: TransactionSummary,
        budgets: List<BudgetEntity>,
        spendByCategory: Map<String, Double>,
        dayOfMonth: Int = java.util.Calendar.getInstance()
            .get(java.util.Calendar.DAY_OF_MONTH),
        daysInMonth: Int = java.util.Calendar.getInstance()
            .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    ): String {

        if (summary.totalCount == 0) {
            return "Quiet month so far. TD will pick things up as you go."
        }

        if (budgets.isEmpty()) {
            val top = spendByCategory.maxByOrNull { it.value }
            if (top == null || top.value <= 0.0) {
                return "${countPhrase(summary.totalCount)} this month."
            }
            return "${top.key}'s been your biggest this month — " +
                    "KSh ${fmt(top.value)} so far. Want to set a budget for it?"
        }

        val usages = budgets.mapNotNull { b ->
            if (b.monthlyLimit <= 0.0) return@mapNotNull null
            val spent = spendByCategory[b.category] ?: 0.0
            Usage(
                category = b.category,
                spent = spent,
                limit = b.monthlyLimit,
                ratio = spent / b.monthlyLimit
            )
        }
        if (usages.isEmpty()) {
            return "${countPhrase(summary.totalCount)} this month."
        }
        val top = usages.maxByOrNull { it.ratio } ?: return ""

        return when {

            top.ratio > 1.0 -> {
                val over = top.spent - top.limit
                val trailing = when {
                    top.ratio >= 1.5 -> "That's quite a lot over."
                    else -> "Might want to ease up for the rest of the month."
                }
                "${top.category}'s past your ${fmt(top.limit)} limit — " +
                        "you're at ${fmt(top.spent)}, KSh ${fmt(over)} over. $trailing"
            }

            top.ratio >= 0.9 -> {
                val remaining = top.limit - top.spent
                val daysLeft = (daysInMonth - dayOfMonth).coerceAtLeast(0)
                if (daysLeft <= 5) {
                    "${top.category}'s at ${fmt(top.spent)} of ${fmt(top.limit)} " +
                            "with only ${daysLeft} day${if (daysLeft == 1) "" else "s"} left. " +
                            "Tight but not over."
                } else {
                    "Easy on ${top.category} — ${fmt(top.spent)} of ${fmt(top.limit)}. " +
                            "KSh ${fmt(remaining)} left and the month's not done."
                }
            }

            top.ratio >= 0.8 -> {
                val remaining = top.limit - top.spent
                "Getting close on ${top.category} — ${fmt(top.spent)} of ${fmt(top.limit)}. " +
                        "KSh ${fmt(remaining)} to spare."
            }

            top.ratio >= 0.5 -> {
                val remaining = top.limit - top.spent
                "${top.category}'s at ${fmt(top.spent)} of ${fmt(top.limit)}, " +
                        "another KSh ${fmt(remaining)} to go."
            }

            else -> {
                "${top.category}'s looking easy — ${fmt(top.spent)} of ${fmt(top.limit)}, " +
                        "plenty of room."
            }
        }
    }

    private data class Usage(
        val category: String,
        val spent: Double,
        val limit: Double,
        val ratio: Double
    )

    private fun fmt(v: Double): String = String.format("%.0f", v)

    private fun countPhrase(n: Int): String =
        "$n transaction${if (n == 1) "" else "s"}"
}