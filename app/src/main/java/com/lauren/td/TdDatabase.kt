package com.lauren.td

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        FundEntity::class
    ],
    version = 4
)
abstract class TdDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun fundDao(): FundDao

    companion object {

        private val DEFAULT_CATEGORIES = listOf(
            "Food", "Transport", "Health", "Internet", "Leisure"
        )

        @Volatile
        private var INSTANCE: TdDatabase? = null

        fun get(context: Context): TdDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    TdDatabase::class.java,
                    "td_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(seedCallback)
                    .build()
                INSTANCE = db
                db
            }
        }

        private val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                for (name in DEFAULT_CATEGORIES) {
                    db.execSQL(
                        "INSERT INTO categories (name, createdAt) VALUES (?, ?)",
                        arrayOf(name, now)
                    )
                }
            }
        }
    }
}