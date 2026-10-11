package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A place the reader asked to keep (M15).
 *
 * Separate from highlights on purpose: a highlight is about a passage and carries colour,
 * note and tags, while a bookmark is only about a *place* — "come back here". Folding one
 * into the other would mean inventing a highlight with no text, which is exactly the kind
 * of lie that later needs undoing.
 *
 * Keyed on [bookContentHash] like everything else, so a book re-imported from a different
 * folder keeps its bookmarks. No foreign key: deleting a book leaves its bookmarks
 * orphaned rather than taking them with it, which is what makes a re-import feel like
 * coming home.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["book_content_hash"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "book_content_hash")
    val bookContentHash: String,

    /** The full Readium locator, which is what reopening the place needs. */
    @ColumnInfo(name = "locator_json")
    val locatorJson: String,

    /** Which resource, kept out of the locator so a bookmark can be matched cheaply. */
    @ColumnInfo(name = "resource_href")
    val resourceHref: String,

    /** Readium's synthetic page, for the list and for recognising the current page. */
    @ColumnInfo(name = "page")
    val page: Int?,

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String?,

    /** First words of the page, so the list reads as places rather than numbers. */
    @ColumnInfo(name = "excerpt")
    val excerpt: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE book_content_hash = :hash ORDER BY page IS NULL, page ASC, created_at ASC")
    fun observeForBook(hash: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * The bookmark on the page being read, if there is one.
     *
     * Matched on resource *and* page rather than on the locator string: two locators for
     * the same page differ in their text snippets, so comparing them would let the same
     * page be bookmarked twice and leave the toggle unable to find what it just added.
     */
    @Query(
        "SELECT * FROM bookmarks WHERE book_content_hash = :hash AND resource_href = :href " +
            "AND ((page IS NULL AND :page IS NULL) OR page = :page) LIMIT 1",
    )
    suspend fun findAt(hash: String, href: String, page: Int?): BookmarkEntity?
}
