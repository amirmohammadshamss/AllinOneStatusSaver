package com.cd.statussaver.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

class MediaRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("status_saver", Context.MODE_PRIVATE)
    val savedDirectory: File get() = File(context.filesDir, "saved").apply { mkdirs() }

    fun folders(): List<Uri> = preferences.getStringSet("folders", emptySet()).orEmpty()
        .sorted().map(Uri::parse)

    fun connectFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val folders = preferences.getStringSet("folders", emptySet()).orEmpty().toMutableSet()
        folders.add(uri.toString())
        preferences.edit { putStringSet("folders", folders) }
    }

    fun disconnectFolders() {
        folders().forEach { uri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        preferences.edit { remove("folders") }
    }

    suspend fun statuses(): List<MediaItem> = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val items = mutableListOf<Pair<Long, MediaItem>>()
        fun scan(directory: DocumentFile) {
            directory.listFiles().forEach { file ->
                coroutine.ensureActive()
                if (file.isFile) {
                    val name = file.name ?: return@forEach
                    val mime = MediaTypes.fromName(name) ?: return@forEach
                    items += file.lastModified() to MediaItem(file.uri, name, mime)
                }
            }
        }
        folders().forEach { uri ->
            val directory = DocumentFile.fromTreeUri(context, uri)
            check(directory?.canRead() == true) { "Folder access expired. Reconnect your WhatsApp folder." }
            fun findStatuses(folder: DocumentFile, depth: Int): DocumentFile? {
                coroutine.ensureActive()
                if (folder.name == ".Statuses") return folder
                val children = folder.listFiles().filter { it.isDirectory }
                children.firstOrNull { it.name == ".Statuses" }?.let { return it }
                if (depth > 0) for (child in children) findStatuses(child, depth - 1)?.let { return it }
                return null
            }
            val statuses = findStatuses(requireNotNull(directory), 2)
                ?: error("No .Statuses folder found. Choose WhatsApp/Media or its .Statuses folder.")
            scan(statuses)
        }
        items.sortedByDescending { it.first }.map { it.second }.distinctBy { it.uri }
    }

    suspend fun saved(): List<MediaItem> = withContext(Dispatchers.IO) {
        savedDirectory.listFiles().orEmpty().filter { it.isFile && MediaTypes.fromName(it.name) != null }
            .sortedByDescending(File::lastModified).map { file ->
                MediaItem(FileProvider.getUriForFile(context, "${context.packageName}.files", file),
                    file.name, requireNotNull(MediaTypes.fromName(file.name)), saved = true)
            }
    }

    suspend fun save(uri: Uri): File = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Select media from the Android document picker." }
        var mime = context.contentResolver.getType(uri).orEmpty().substringBefore(';')
        if (MediaTypes.extension(mime) == null) {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) mime = MediaTypes.fromName(cursor.getString(0)).orEmpty()
            }
        }
        val extension = MediaTypes.extension(mime) ?: error("This image or video format is not supported.")
        val coroutine = currentCoroutineContext()
        context.contentResolver.openInputStream(uri)?.use { input -> saveStream(input, extension) { !coroutine.isActive } }
            ?: error("Could not read the selected media.")
    }

    // A partial download must never appear in the gallery, including after process death.
    fun saveStream(input: InputStream, extension: String, expectedBytes: Long? = null, cancelled: () -> Boolean = { false }): File {
        return MediaFileStore(savedDirectory).save(input, extension, expectedBytes, cancelled)
    }

    suspend fun delete(item: MediaItem) = withContext(Dispatchers.IO) {
        require(item.saved)
        val file = File(savedDirectory, item.name)
        require(file.canonicalFile.parentFile == savedDirectory.canonicalFile)
        check(file.delete()) { "Could not delete this file." }
    }

    suspend fun export(item: MediaItem, destination: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(item.uri) ?: error("Cannot read the media.")
        input.use { source ->
            val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("Cannot write to this location.")
            output.use { source.copyTo(it) }
        }
    }

    companion object { const val MAX_MEDIA_BYTES = MediaFileStore.MAX_MEDIA_BYTES }
}
