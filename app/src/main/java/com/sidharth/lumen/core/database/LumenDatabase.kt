package com.sidharth.lumen.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        TagEntity::class,
        HighlightTagEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "lumen.db"
    }
}
