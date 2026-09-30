package io.github.joaquinjbg.efectivo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun sourceToString(source: ExpenseSource): String = source.name

    @TypeConverter
    fun stringToSource(value: String): ExpenseSource = ExpenseSource.valueOf(value)
}

/** Adds QuickButton.compact, which splits coin buttons out of the category grid. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE quick_buttons ADD COLUMN compact INTEGER NOT NULL DEFAULT 0"
        )
        // The seeded coin buttons are the ones whose label is just the amount.
        connection.execSQL(
            "UPDATE quick_buttons SET compact = 1 WHERE emoji = '🪙'"
        )
    }
}

@Database(
    entities = [Category::class, Expense::class, CashMovement::class, QuickButton::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EfectivoDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun cashMovementDao(): CashMovementDao
    abstract fun quickButtonDao(): QuickButtonDao

    companion object {
        @Volatile
        private var instance: EfectivoDatabase? = null

        fun get(context: Context): EfectivoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EfectivoDatabase::class.java,
                    "efectivo.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
