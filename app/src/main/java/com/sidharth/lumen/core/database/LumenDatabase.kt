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
        LookupEventEntity::class,
        HighlightFts::class,
        NoteFts::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun lookupEventDao(): LookupEventDao
    abstract fun notesDao(): NotesDao

    companion object {
        const val NAME = "lumen.db"

        /** Kept in step with the @Database version; backups record it for compatibility. */
        const val SCHEMA_VERSION = 4
    }
}
