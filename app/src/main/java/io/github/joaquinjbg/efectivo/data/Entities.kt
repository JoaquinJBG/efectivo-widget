package io.github.joaquinjbg.efectivo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ExpenseSource { WIDGET, APP }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "expenses",
    indices = [Index("categoryId"), Index("timestamp")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val categoryId: Long,
    val timestamp: Long,
    val note: String? = null,
    val source: ExpenseSource,
)

@Entity(
    tableName = "cash_movements",
    indices = [Index("timestamp")],
)
data class CashMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Positive = cash added (ATM withdrawal); negative = correction downwards. */
    val amountCents: Long,
    val timestamp: Long,
    val note: String? = null,
)

@Entity(
    tableName = "quick_buttons",
    indices = [Index("categoryId")],
)
data class QuickButton(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val emoji: String,
    val categoryId: Long,
    /** null = ask for the amount in the quick-entry dialog. */
    val fixedAmountCents: Long?,
    val sortOrder: Int,
    val enabled: Boolean = true,
)

/** An expense joined with the name/emoji of its category. */
data class ExpenseWithCategory(
    val id: Long,
    val amountCents: Long,
    val categoryId: Long,
    val timestamp: Long,
    val note: String?,
    val source: ExpenseSource,
    val categoryName: String,
    val categoryEmoji: String,
)

/** One row of the monthly breakdown. */
data class CategoryTotal(
    val categoryId: Long,
    val categoryName: String,
    val categoryEmoji: String,
    val totalCents: Long,
)
