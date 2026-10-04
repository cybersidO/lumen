package com.sidharth.lumen.core.books

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sidharth.lumen.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A folder the user has granted, with a readable name for the settings list. */
data class BookFolder(val uri: Uri, val label: String)

/**
 * The folders Lumen is allowed to look in for books (M11).
 *
 * Android has no permission that means "find my EPUBs" — the only thing that searches the
 * whole device is `MANAGE_EXTERNAL_STORAGE` ("All files access"), which grants read/write
 * to everything and cannot be scoped. Rather than take that, Lumen remembers the folders
 * the user picks through the system picker: the grants are persisted across reboots, so
 * after the first setup a rescan is one tap and needs nothing further.
 *
 * Grants that have gone stale — folder deleted, SD card removed, permission revoked in
 * Settings — are dropped on read, so the list never offers a folder that would fail.
 */
@Singleton
class BookFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    val folders: Flow<List<BookFolder>> = dataStore.data.map { prefs ->
        prefs[Key].orEmpty().mapNotNull { raw ->
            val uri = runCatching { raw.toUri() }.getOrNull() ?: return@mapNotNull null
            if (!hasGrant(uri)) null else BookFolder(uri, uri.readableLabel())
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * Takes a persistable grant and remembers the folder. Returns false when Android
     * refuses the grant, which it does for the storage root and for `Download` itself.
     */
    suspend fun add(uri: Uri): Boolean = withContext(ioDispatcher) {
        val taken = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!taken) return@withContext false
        dataStore.edit { prefs ->
            prefs[Key] = prefs[Key].orEmpty() + uri.toString()
        }
        true
    }

    suspend fun remove(uri: Uri) = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        dataStore.edit { prefs ->
            prefs[Key] = prefs[Key].orEmpty() - uri.toString()
        }
        Unit
    }

    suspend fun current(): List<BookFolder> = folders.first()

    private fun hasGrant(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    /** "primary:Download/Books" → "Download/Books"; falls back to the raw id. */
    private fun Uri.readableLabel(): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return toString()
        return id.substringAfter(':').ifBlank { id }
    }

    private companion object {
        val Key = stringSetPreferencesKey("book_folders")
    }
}
