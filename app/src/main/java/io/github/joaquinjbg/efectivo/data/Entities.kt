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
    /**
     * Borrar el botón archiva su categoría en vez de eliminarla: así deja de ofrecerse
     * para gastos nuevos, pero los que ya estaban siguen mostrando su nombre en el
     * historial y en el resumen del mes.
     */
    val archived: Boolean = false,
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
    /** Positivo = efectivo que entra (cajero); negativo = corrección a la baja. */
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
    /** null = preguntar el importe en el diálogo de entrada rápida. */
    val fixedAmountCents: Long?,
    val sortOrder: Int,
    val enabled: Boolean = true,
    /**
     * Monedas y billetes: se pintan pequeños, en su propia fila bajo las categorías.
     * Los botones de categoría son las baldosas grandes.
     */
    val compact: Boolean = false,
)

/** Un gasto junto con el nombre y el emoji de su categoría. */
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

/**
 * Todas las cifras de la cabecera del widget, en una sola fila.
 *
 * El widget es la parte donde la latencia importa, así que lee esto en vez de
 * suscribirse a cuatro consultas agregadas por separado.
 */
data class WidgetTotals(
    val balanceCents: Long,
    val todayCents: Long,
    val weekCents: Long,
    val monthCents: Long,
)

enum class MovementType { GASTO, INGRESO }

/**
 * Una línea del historial, venga de un gasto o de una entrada de efectivo.
 *
 * El historial mezcla las dos tablas, así que necesita una forma común de hablar de
 * ambas. [categoryName] y [categoryEmoji] solo los traen los gastos.
 */
data class LedgerEntry(
    val id: Long,
    val type: MovementType,
    /** Siempre positivo. El signo lo lleva [type], salvo en correcciones a la baja. */
    val amountCents: Long,
    val timestamp: Long,
    val note: String?,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val categoryEmoji: String? = null,
) {
    /** Una entrada de efectivo en negativo es una corrección del saldo, no un ingreso. */
    val isCorrection: Boolean get() = type == MovementType.INGRESO && amountCents < 0
}

/** Una línea del desglose mensual. */
data class CategoryTotal(
    val categoryId: Long,
    val categoryName: String,
    val categoryEmoji: String,
    val totalCents: Long,
)
