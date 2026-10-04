package com.sidharth.lumen.core.books

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import com.sidharth.lumen.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Searches the whole device for EPUBs (1.4.0).
 *
 * This needs "All files access" (`MANAGE_EXTERNAL_STORAGE`) on Android 11+, which is the
 * only permission Android offers that can see files the user did not hand over one folder
 * at a time. It cannot be narrowed to a single file type: the grant is broad, and that is
 * why 1.3.0 shipped remembered folders instead. The owner used that and asked for the
 * thorough search anyway, so the permission is now declared and requested — Lumen reads
 * only, and only ever looks for `.epub`.
 *
 * Google Play bans this permission for almost all apps. That restriction does not apply
 * here because Lumen is sideloaded and never published, but it is the reason the README
 * spells the grant out.
 *
 * The walk itself is plain [File] traversal, which is far faster than SAF's per-folder
 * content-provider queries — the whole of internal storage takes a second or two.
 */
@Singleton
class DeviceBookSearch @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** True when Lumen may read the whole of shared storage. */
    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /**
     * The Settings screen that grants it. Android 11+ only — below that it is an ordinary
     * runtime permission and the caller asks for it the usual way.
     */
    fun grantIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        // The per-app screen, falling back to the global list on devices that don't
        // honour the targeted action.
        return Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
    }

    fun fallbackGrantIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    /**
     * Walks every readable volume for `.epub` files.
     *
     * [onProgress] reports folders visited so the UI can show motion on a large device.
     * Android's own data directories are skipped: they hold no books and `Android/data`
     * is unreadable anyway, so descending into them wastes the user's time.
     */
    suspend fun search(onProgress: (foldersSeen: Int) -> Unit = {}): List<FoundBook> =
        withContext(ioDispatcher) { searchRoots(storageRoots(), onProgress) }

    /** The volumes worth walking: internal storage plus any SD card or USB drive. */
    private fun storageRoots(): List<File> =
        buildList {
                add(Environment.getExternalStorageDirectory())
                // Secondary volumes (SD card, USB) when the device exposes them.
                context.getExternalFilesDirs(null)
                    .filterNotNull()
                    .mapNotNull { it.volumeRoot() }
                    .forEach { if (none { r -> r.absolutePath == it.absolutePath }) add(it) }
        }.filter { it.isDirectory && it.canRead() }

    /**
     * The walk itself, over explicit roots so it can be tested against a temp directory
     * instead of a real phone's storage.
     */
    internal suspend fun searchRoots(
        roots: List<File>,
        onProgress: (foldersSeen: Int) -> Unit = {},
    ): List<FoundBook> {
            val found = LinkedHashMap<String, FoundBook>()
            var folders = 0
            for (root in roots) {
                walk(
                    dir = root,
                    root = root,
                    depth = 0,
                    onFile = { file ->
                        found.putIfAbsent(
                            file.absolutePath,
                            FoundBook(
                                uri = Uri.fromFile(file),
                                displayName = file.name,
                                sizeBytes = file.length().takeIf { it > 0 },
                                relativePath = file.parentFile
                                    ?.absolutePath
                                    ?.removePrefix(root.absolutePath)
                                    ?.trim('/')
                                    .orEmpty(),
                            ),
                        )
                    },
                    onFolder = {
                        folders++
                        onProgress(folders)
                    },
                )
            }
            return found.values.sortedWith(compareBy({ it.relativePath }, { it.displayName }))
    }

    private suspend fun walk(
        dir: File,
        root: File,
        depth: Int,
        onFile: (File) -> Unit,
        onFolder: () -> Unit,
    ) {
        if (depth > MAX_DEPTH) return
        currentCoroutineContext().ensureActive()
        onFolder()
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (child.name.startsWith('.')) continue
                if (child.name in SKIPPED_DIRS && dir.absolutePath == root.absolutePath) continue
                walk(child, root, depth + 1, onFile, onFolder)
            } else if (child.name.endsWith(".epub", ignoreCase = true) && child.canRead()) {
                onFile(child)
            }
        }
    }


    /** `/storage/XXXX-XXXX/Android/data/<pkg>/files` → `/storage/XXXX-XXXX`. */
    private fun File.volumeRoot(): File? {
        var cursor: File? = this
        while (cursor != null && cursor.name != "Android") cursor = cursor.parentFile
        return cursor?.parentFile
    }

    private companion object {
        /** Deep enough for any real library; a guard against pathological trees. */
        const val MAX_DEPTH = 14

        /** No books live here, and `Android/data` is unreadable on modern Android anyway. */
        val SKIPPED_DIRS = setOf("Android", "DCIM", "Pictures", "Movies", "Music", "Ringtones")
    }
}
