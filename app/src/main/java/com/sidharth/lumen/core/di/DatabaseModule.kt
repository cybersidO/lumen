package com.sidharth.lumen.core.di

import android.content.Context
import androidx.room.Room
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.HighlightDao
import com.sidharth.lumen.core.database.LumenDatabase
import com.sidharth.lumen.core.database.LookupEventDao
import com.sidharth.lumen.core.database.MIGRATION_1_2
import com.sidharth.lumen.core.database.MIGRATION_2_3
import com.sidharth.lumen.core.database.MIGRATION_3_4
import com.sidharth.lumen.core.database.MIGRATION_4_5
import com.sidharth.lumen.core.database.ReviewDao
import com.sidharth.lumen.core.database.NotesDao
import com.sidharth.lumen.core.database.NoteDao
import com.sidharth.lumen.core.database.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LumenDatabase =
        Room.databaseBuilder(context, LumenDatabase::class.java, LumenDatabase.NAME)
            // No fallbackToDestructiveMigration, ever. Losing highlights silently on a
            // schema change is the one failure this app cannot have (§4).
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideBookDao(database: LumenDatabase): BookDao = database.bookDao()

    @Provides
    fun provideHighlightDao(database: LumenDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun provideNoteDao(database: LumenDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTagDao(database: LumenDatabase): TagDao = database.tagDao()

    @Provides
    fun provideLookupEventDao(database: LumenDatabase): LookupEventDao =
        database.lookupEventDao()

    @Provides
    fun provideNotesDao(database: LumenDatabase): NotesDao = database.notesDao()

    @Provides
    fun provideReviewDao(database: LumenDatabase): ReviewDao = database.reviewDao()
}
