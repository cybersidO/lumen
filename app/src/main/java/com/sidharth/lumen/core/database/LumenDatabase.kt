package com.sidharth.lumen.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, HighlightEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        const val NAME = "lumen.db"
    }
}
