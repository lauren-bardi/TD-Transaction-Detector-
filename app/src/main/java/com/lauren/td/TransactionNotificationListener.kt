package com.lauren.td

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class TransactionNotificationListener :
    NotificationListenerService() {

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        // Ignore TD's own notifications
        if (sbn.packageName == packageName) {
            return
        }

        // Google Messages is currently the
        // delivery source for our M-Pesa SMS.
        if (
            sbn.packageName !=
            "com.google.android.apps.messaging"
        ) {
            return
        }

        val extras = sbn.notification.extras

        val messageBundles =
            extras.getParcelableArray(
                Notification.EXTRA_MESSAGES
            ) ?: return

        val messages =
            Notification.MessagingStyle.Message
                .getMessagesFromBundleArray(
                    messageBundles
                )

        /*
         * Google Messages can give us several messages
         * from the conversation.
         *
         * We check them from newest to oldest.
         */
        val newestMessages =
            messages.sortedByDescending {
                it.timestamp
            }

        for (message in newestMessages) {

            val messageText =
                message.text
                    ?.toString()
                    ?: continue

            val transaction =
                detectTransaction(
                    text = messageText,
                    timestamp = message.timestamp
                )

            if (transaction != null) {

                TransactionOverlayManager.show(
                    context = this,
                    transaction = transaction
                )

                // Stop after the first valid transaction.
                return
            }
        }
    }

    private fun detectTransaction(
        text: String,
        timestamp: Long
    ): TransactionData? {

        /*
         * ------------------------------------------------
         * 1. FULIZA
         * ------------------------------------------------
         *
         * Fuliza repayment is NOT treated as an ordinary
         * spending transaction.
         */
        if (
            text.contains(
                "outstanding Fuliza M-PESA",
                ignoreCase = true
            )
        ) {
            return null
        }

        /*
         * ------------------------------------------------
         * 2. TRANSACTION REFERENCE
         * ------------------------------------------------
         */
        val referenceMatch =
            Regex(
                """\b([A-Z0-9]{8,})\b"""
            ).find(text)
                ?: return null

        val reference =
            referenceMatch.groupValues[1]

        /*
         * ------------------------------------------------
         * 3. SENT TRANSACTION
         * ------------------------------------------------
         *
         * Example:
         * "Ksh5.00 sent to NAME..."
         */
        val sentMatch =
            Regex(
                """Confirmed\.\s*Ksh\s*([\d,]+(?:\.\d{1,2})?)\s+sent\s+to\s+(.+?)\s+(07\d{7,8})\s+on""",
                RegexOption.IGNORE_CASE
            ).find(text)

        if (sentMatch != null) {

            val amount =
                sentMatch
                    .groupValues[1]
                    .replace(",", "")
                    .toDoubleOrNull()
                    ?: return null

            val recipient =
                sentMatch
                    .groupValues[2]
                    .trim()

            val cost =
                extractTransactionCost(text)

            return TransactionData(
                reference = reference,
                amount = amount,
                type = "SENT",
                otherParty = recipient,
                transactionCost = cost,
                time = timestamp,
                source = "M-PESA"
            )
        }

        /*
         * ------------------------------------------------
         * 4. RECEIVED TRANSACTION
         * ------------------------------------------------
         *
         * Example:
         * "You have received Ksh5.00 from NAME..."
         */
        val receivedMatch =
            Regex(
                """Confirmed\.\s*You have received\s*Ksh\s*([\d,]+(?:\.\d{1,2})?)\s+from\s+(.+?)\s+(07\d{7,8})\s+on""",
                RegexOption.IGNORE_CASE
            ).find(text)

        if (receivedMatch != null) {

            val amount =
                receivedMatch
                    .groupValues[1]
                    .replace(",", "")
                    .toDoubleOrNull()
                    ?: return null

            val sender =
                receivedMatch
                    .groupValues[2]
                    .trim()

            return TransactionData(
                reference = reference,
                amount = amount,
                type = "RECEIVED",
                otherParty = sender,
                transactionCost = null,
                time = timestamp,
                source = "M-PESA"
            )
        }

        /*
         * ------------------------------------------------
         * Nothing recognized
         * ------------------------------------------------
         */
        return null
    }

    private fun extractTransactionCost(
        text: String
    ): Double? {

        val costMatch =
            Regex(
                """Transaction cost,\s*Ksh\s*([\d,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            ).find(text)
                ?: return null

        return costMatch
            .groupValues[1]
            .replace(",", "")
            .toDoubleOrNull()
    }
}