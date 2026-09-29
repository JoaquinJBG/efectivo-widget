package io.github.joaquinjbg.efectivo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun sourceToString(source: ExpenseSource): String = source.name

    @TypeConverter
    fun stringToSource(value: String): ExpenseSource = ExpenseSource.valueOf(value)
}

@Database(
    entities = [Category::class, Expense::class, CashMovement::class, QuickButton::class],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
