package io.github.joaquinjbg.efectivo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<Category>): List<Long>
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses")
    fun observeTotal(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses")
    suspend fun total(): Long

    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :from AND timestamp < :until"
    )
    fun observeTotalBetween(from: Long, until: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :from AND timestamp < :until"
    )
    suspend fun totalBetween(from: Long, until: Long): Long

    @Query(
        "SELECT e.id, e.amountCents, e.categoryId, e.timestamp, e.note, e.source, " +
            "c.name AS categoryName, c.emoji AS categoryEmoji " +
            "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
            "ORDER BY e.timestamp DESC, e.id DESC"
    )
    fun observeAllWithCategory(): Flow<List<ExpenseWithCategory>>

    @Query(
        "SELECT e.id, e.amountCents, e.categoryId, e.timestamp, e.note, e.source, " +
            "c.name AS categoryName, c.emoji AS categoryEmoji " +
            "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
            "WHERE e.timestamp >= :from AND e.timestamp < :until " +
            "ORDER BY e.timestamp DESC, e.id DESC"
    )
    fun observeBetweenWithCategory(from: Long, until: Long): Flow<List<ExpenseWithCategory>>

    @Query(
        "SELECT e.id, e.amountCents, e.categoryId, e.timestamp, e.note, e.source, " +
            "c.name AS categoryName, c.emoji AS categoryEmoji " +
            "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
            "WHERE e.timestamp >= :from AND e.timestamp < :until " +
            "ORDER BY e.timestamp DESC, e.id DESC"
    )
    suspend fun betweenWithCategory(from: Long, until: Long): List<ExpenseWithCategory>

    @Query(
        "SELECT e.id, e.amountCents, e.categoryId, e.timestamp, e.note, e.source, " +
            "c.name AS categoryName, c.emoji AS categoryEmoji " +
            "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
            "ORDER BY e.timestamp DESC, e.id DESC LIMIT 1"
    )
    suspend fun latestWithCategory(): ExpenseWithCategory?

    @Query(
        "SELECT " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM cash_movements) - " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses) AS balanceCents, " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :dayFrom AND timestamp < :dayUntil) AS todayCents, " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :weekFrom AND timestamp < :weekUntil) AS weekCents, " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :monthFrom AND timestamp < :monthUntil) AS monthCents"
    )
    fun observeWidgetTotals(
        dayFrom: Long,
        dayUntil: Long,
        weekFrom: Long,
        weekUntil: Long,
        monthFrom: Long,
        monthUntil: Long,
    ): Flow<WidgetTotals>

    @Query(
        "SELECT " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM cash_movements) - " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses) AS balanceCents, " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :dayFrom AND timestamp < :dayUntil) AS todayCents, " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :weekFrom AND timestamp < :weekUntil) AS weekCents, " +
            "(SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE timestamp >= :monthFrom AND timestamp < :monthUntil) AS monthCents"
    )
    suspend fun widgetTotals(
        dayFrom: Long,
        dayUntil: Long,
        weekFrom: Long,
        weekUntil: Long,
        monthFrom: Long,
        monthUntil: Long,
    ): WidgetTotals

    @Query(
        "SELECT c.id AS categoryId, c.name AS categoryName, c.emoji AS categoryEmoji, " +
            "COALESCE(SUM(e.amountCents), 0) AS totalCents " +
            "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
            "WHERE e.timestamp >= :from AND e.timestamp < :until " +
            "GROUP BY c.id, c.name, c.emoji ORDER BY totalCents DESC"
    )
    fun observeTotalsByCategory(from: Long, until: Long): Flow<List<CategoryTotal>>
}

@Dao
interface CashMovementDao {
    @Insert
    suspend fun insert(movement: CashMovement): Long

    @Query("DELETE FROM cash_movements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM cash_movements")
    fun observeTotal(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM cash_movements")
    suspend fun total(): Long

    @Query("SELECT * FROM cash_movements ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<CashMovement>>
}

@Dao
interface QuickButtonDao {
    @Query("SELECT * FROM quick_buttons ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<QuickButton>>

    @Query("SELECT * FROM quick_buttons WHERE enabled = 1 ORDER BY sortOrder, id")
    fun observeEnabled(): Flow<List<QuickButton>>

    @Query("SELECT * FROM quick_buttons WHERE enabled = 1 ORDER BY sortOrder, id")
    suspend fun enabled(): List<QuickButton>

    @Query("SELECT * FROM quick_buttons WHERE id = :id")
    suspend fun getById(id: Long): QuickButton?

    @Query("SELECT COUNT(*) FROM quick_buttons")
    suspend fun count(): Int

    @Insert
    suspend fun insert(button: QuickButton): Long

    @Insert
    suspend fun insertAll(buttons: List<QuickButton>): List<Long>

    @Update
    suspend fun update(button: QuickButton)

    @Update
    suspend fun updateAll(buttons: List<QuickButton>)

    @Query("DELETE FROM quick_buttons WHERE id = :id")
    suspend fun deleteById(id: Long)
}
