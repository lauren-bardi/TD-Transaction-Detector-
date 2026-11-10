package com.lauren.td

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.withContext

object TransactionOverlayManager {

    private var overlayView: LinearLayout? = null
    private var scope: CoroutineScope? = null
    private var isShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(
        context: Context,
        transaction: TransactionData
    ) {

        if (isShowing) {
            return
        }
        isShowing = true

        val appContext = context.applicationContext
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = currentScope

        val db = TdDatabase.get(appContext)
        val txDao = db.transactionDao()
        val categoryDao = db.categoryDao()
        val fundDao = db.fundDao()

        val windowManager =
            appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 1. Save the transaction immediately with no category.
        //    If a fund is currently being tracked, attach it automatically.
        currentScope.launch {
            try {
                val activeFund = try {
                    fundDao.getActive()?.name
                } catch (_: Exception) {
                    null
                }
                txDao.insert(
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
                        status = "AWAITING_CATEGORY",
                        fund = activeFund
                    )
                )
            } catch (_: Exception) {
            }
        }

        // 2. Load categories, then build and show the overlay.
        currentScope.launch {
            val categories = try {
                categoryDao.getAll().map { it.name }
            } catch (_: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                if (currentScope !== scope) return@withContext
                showCategoryOverlay(
                    appContext = appContext,
                    transaction = transaction,
                    categories = categories,
                    windowManager = windowManager,
                    currentScope = currentScope,
                    txDao = txDao
                )
            }
        }
    }

    private fun showCategoryOverlay(
        appContext: Context,
        transaction: TransactionData,
        categories: List<String>,
        windowManager: WindowManager,
        currentScope: CoroutineScope,
        txDao: TransactionDao
    ) {

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 35, 40, 35)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 35f
            }
        }

        showCategoryQuestion(
            container = container,
            windowManager = windowManager,
            scope = currentScope,
            dao = txDao,
            appContext = appContext,
            transaction = transaction,
            categories = categories
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        overlayView = container
        try {
            windowManager.addView(container, params)
        } catch (_: Exception) {
            overlayView = null
            isShowing = false
        }
    }

    private fun showCategoryQuestion(
        container: LinearLayout,
        windowManager: WindowManager,
        scope: CoroutineScope,
        dao: TransactionDao,
        appContext: Context,
        transaction: TransactionData,
        categories: List<String>
    ) {

        container.addView(TextView(appContext).apply {
            text = "TD"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        })

        container.addView(TextView(appContext).apply {
            val partyText = transaction.otherParty?.let { "\nTo: $it" } ?: ""
            text = "KSh ${String.format("%.2f", transaction.amount)} detected$partyText"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 15)
        })

        if (categories.isEmpty()) {
            container.addView(TextView(appContext).apply {
                text = "No categories available. Open TD to add some."
                textSize = 16f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 15)
            })
            container.addView(Button(appContext).apply {
                text = "Close"
                setOnClickListener { removeOverlay(windowManager) }
            })
            return
        }

        container.addView(TextView(appContext).apply {
            text = "What was this for?"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        })

        for (category in categories) {
            container.addView(Button(appContext).apply {
                text = category
                setOnClickListener {
                    scope.launch {
                        val existing = dao.findByReference(transaction.reference)
                        if (existing != null) {
                            dao.update(
                                existing.copy(
                                    category = category,
                                    status = "AWAITING_INTENT"
                                )
                            )
                        }
                    }.invokeOnCompletion {
                        mainHandler.post {
                            container.removeAllViews()
                            showIntentQuestion(
                                container = container,
                                windowManager = windowManager,
                                scope = scope,
                                dao = dao,
                                appContext = appContext,
                                transaction = transaction,
                                selectedCategory = category
                            )
                        }
                    }
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        container.addView(Button(appContext).apply {
            text = "Not now"
            setOnClickListener { removeOverlay(windowManager) }
        })
    }

    private fun showIntentQuestion(
        container: LinearLayout,
        windowManager: WindowManager,
        scope: CoroutineScope,
        dao: TransactionDao,
        appContext: Context,
        transaction: TransactionData,
        selectedCategory: String
    ) {

        container.addView(TextView(appContext).apply {
            text = "TD"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        })

        container.addView(TextView(appContext).apply {
            text = "KSh ${String.format("%.2f", transaction.amount)} — $selectedCategory"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 10)
        })

        container.addView(TextView(appContext).apply {
            text = "What was the nature of this spending?"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 15)
        })

        val intents = listOf(
            "Planned" to "PLANNED",
            "Unplanned" to "UNPLANNED",
            "Unexpected but necessary" to "UNEXPECTED_BUT_NECESSARY"
        )

        for ((label, value) in intents) {
            container.addView(Button(appContext).apply {
                text = label
                setOnClickListener {
                    scope.launch {
                        val existing = dao.findByReference(transaction.reference)
                        if (existing != null) {
                            dao.update(
                                existing.copy(intent = value, status = "COMPLETE")
                            )
                        }
                    }.invokeOnCompletion {
                        mainHandler.post {
                            Toast.makeText(appContext, "Saved", Toast.LENGTH_SHORT).show()
                            removeOverlay(windowManager)
                        }
                    }
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        container.addView(Button(appContext).apply {
            text = "Skip for now"
            setOnClickListener {
                scope.launch {
                    val existing = dao.findByReference(transaction.reference)
                    if (existing != null) {
                        dao.update(
                            existing.copy(intent = null, status = "SKIPPED")
                        )
                    }
                }.invokeOnCompletion {
                    mainHandler.post { removeOverlay(windowManager) }
                }
            }
        })
    }

    private fun removeOverlay(windowManager: WindowManager) {
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
        isShowing = false
    }
}