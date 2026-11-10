package com.lauren.td

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object TransactionOverlayManager {

    private var overlayView: LinearLayout? = null
    private var scope: CoroutineScope? = null

    fun show(
        context: Context,
        transaction: TransactionData
    ) {

        if (overlayView != null) {
            return
        }

        val appContext = context.applicationContext
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = currentScope

        val dao = TdDatabase.get(appContext).transactionDao()

        // 1. Save the transaction immediately with no category.
        currentScope.launch {
            dao.insert(
                TransactionEntity(
                    reference = transaction.reference,
                    amount = transaction.amount,
                    type = transaction.type,
                    otherParty = transaction.otherParty,
                    transactionCost = transaction.transactionCost,
                    time = transaction.time,
                    source = transaction.source,
                    category = null,
                    intent = null,
                    status = "AWAITING_CATEGORY"
                )
            )
        }

        val windowManager =
            appContext.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        val container =
            LinearLayout(appContext)

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            40,
            35,
            40,
            35
        )

        val background =
            GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 35f
            }

        container.background = background

        val title =
            TextView(appContext).apply {
                text = "TD"
                textSize = 22f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
            }

        container.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val amount =
            TextView(appContext).apply {

                val partyText =
                    transaction.otherParty
                        ?.let {
                            "\nTo: $it"
                        }
                        ?: ""

                text =
                    "KSh ${
                        String.format(
                            "%.2f",
                            transaction.amount
                        )
                    } detected$partyText"

                textSize = 20f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER

                setPadding(
                    0,
                    20,
                    0,
                    15
                )
            }

        container.addView(amount)

        val question =
            TextView(appContext).apply {
                text = "What was this for?"
                textSize = 18f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
            }

        container.addView(question)

        val categories =
            listOf(
                "Food",
                "Health",
                "Transport",
                "Education",
                "Leisure",
                "Other"
            )

        for (category in categories) {

            val button =
                Button(appContext).apply {

                    text = category

                    setOnClickListener {

                        currentScope.launch {
                            val existing =
                                dao.findByReference(transaction.reference)
                            if (existing != null) {
                                dao.update(
                                    existing.copy(
                                        category = category,
                                        status = "AWAITING_INTENT"
                                    )
                                )
                            }
                        }.invokeOnCompletion {

                            Toast.makeText(
                                appContext,
                                "KSh ${transaction.amount} classified as $category",
                                Toast.LENGTH_SHORT
                            ).show()

                            removeOverlay(windowManager)
                        }
                    }
                }

            container.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val notNow =
            Button(appContext).apply {

                text = "Not now"

                setOnClickListener {
                    removeOverlay(windowManager)
                }
            }

        container.addView(notNow)

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity = Gravity.CENTER

        overlayView = container

        windowManager.addView(
            container,
            params
        )
    }

    private fun removeOverlay(
        windowManager: WindowManager
    ) {

        val view = overlayView

        if (view != null) {

            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }

            overlayView = null
        }

        scope?.cancel()
        scope = null
    }
}