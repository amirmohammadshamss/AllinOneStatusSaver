package com.cd.statussaver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val input = inputData.getString(URL_KEY) ?: return@withContext Result.failure()
            val resolver = PublicMediaResolver()
            val resolved = resolver.resolve(input)
            val connection = resolver.open(resolved)
            try {
                val extension = MediaTypes.extension(connection.contentType.orEmpty())
                    ?: error("The server did not return an image or video.")
                check(connection.contentLengthLong <= MediaRepository.MAX_MEDIA_BYTES) { "The media exceeds 250 MB." }
                connection.inputStream.use { stream ->
                    MediaRepository(applicationContext).saveStream(stream, extension,
                        expectedBytes = connection.contentLengthLong.takeIf { it >= 0 }) { isStopped }
                }
            } finally { connection.disconnect() }
            Result.success(workDataOf(MESSAGE_KEY to "Media saved."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf(MESSAGE_KEY to "Download failed. Check your connection and try again."))
        } catch (error: Exception) {
            Result.failure(workDataOf(MESSAGE_KEY to (error.message ?: "Download failed.")))
        }
    }

    companion object {
        const val URL_KEY = "url"
        const val MESSAGE_KEY = "message"
        const val WORK_NAME = "media_download"
    }
}
