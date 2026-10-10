package com.cd.statussaver.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun commitsCompleteMediaAndLeavesNoPartialFile() {
        val directory = temporary.newFolder()
        val content = byteArrayOf(1, 2, 3, 4)
        val result = MediaFileStore(directory).save(ByteArrayInputStream(content), "mp4")
        assertArrayEquals(content, result.readBytes())
        assertEquals(listOf(result.name), directory.listFiles()!!.map { it.name })
    }

    @Test fun cleansPartialFilesWhenReadingFails() {
        val directory = temporary.newFolder()
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("Disconnected")
        }
        assertTrue(runCatching { MediaFileStore(directory).save(broken, "mp4") }.isFailure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun rejectsEmptyAndOversizedMedia() {
        val directory = temporary.newFolder()
        val store = MediaFileStore(directory, maxBytes = 3)
        assertTrue(runCatching { store.save(ByteArrayInputStream(byteArrayOf()), "jpg") }.isFailure)
        assertTrue(runCatching { store.save(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), "jpg") }.isFailure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun cancellationDoesNotLeaveSavedOrPartialMedia() {
        val directory = temporary.newFolder()
        assertTrue(runCatching { MediaFileStore(directory).save(ByteArrayInputStream(byteArrayOf(1)), "jpg") { true } }.isFailure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun rejectsTruncatedDownloadsAndCleansThePartialCopy() {
        val directory = temporary.newFolder()
        assertTrue(runCatching {
            MediaFileStore(directory).save(ByteArrayInputStream(byteArrayOf(1, 2)), "mp4", expectedBytes = 3)
        }.isFailure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun commitsWhenTheDeclaredDownloadLengthMatches() {
        val content = byteArrayOf(1, 2, 3)
        val result = MediaFileStore(temporary.newFolder()).save(ByteArrayInputStream(content), "mp4", expectedBytes = 3)
        assertArrayEquals(content, result.readBytes())
    }

    @Test fun cancellationAtEndOfStreamPreventsCommit() {
        val directory = temporary.newFolder()
        var cancelled = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count == -1) cancelled = true
                return count
            }
        }
        assertTrue(runCatching { MediaFileStore(directory).save(input, "jpg") { cancelled } }.isFailure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
