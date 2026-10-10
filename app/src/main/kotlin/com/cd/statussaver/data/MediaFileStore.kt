package com.cd.statussaver.data

import java.io.File
import java.io.InputStream
import java.util.UUID

class MediaFileStore(private val directory: File, private val maxBytes: Long = MAX_MEDIA_BYTES) {
    fun save(input: InputStream, extension: String, expectedBytes: Long? = null, cancelled: () -> Boolean = { false }): File {
        require(extension in setOf("jpg", "png", "webp", "gif", "mp4", "webm", "3gp"))
        require(expectedBytes == null || expectedBytes in 1..maxBytes) { "The media has an invalid or excessive size." }
        check(directory.isDirectory || directory.mkdirs()) { "Could not create the media folder." }
        val id = UUID.randomUUID().toString()
        val partial = File(directory, "$id.part")
        val destination = File(directory, "status_$id.$extension")
        try {
            partial.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    check(!cancelled()) { "Download cancelled." }
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= maxBytes) { "The media exceeds the download size limit." }
                    output.write(buffer, 0, count)
                }
                check(total > 0) { "The media file is empty." }
                check(expectedBytes == null || total == expectedBytes) { "The download was incomplete. Try again." }
            }
            check(!cancelled()) { "Download cancelled." }
            check(partial.renameTo(destination)) { "Could not finish saving the media." }
            return destination
        } finally { partial.delete() }
    }

    companion object { const val MAX_MEDIA_BYTES = 250L * 1024 * 1024 }
}
