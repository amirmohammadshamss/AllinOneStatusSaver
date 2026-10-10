package com.cd.statussaver

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cd.statussaver.data.DownloadWorker
import com.cd.statussaver.data.MediaItem
import com.cd.statussaver.data.MediaRepository
import com.cd.statussaver.data.PublicMediaResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.UUID

data class SaverState(
    val statuses: List<MediaItem> = emptyList(),
    val saved: List<MediaItem> = emptyList(),
    val folderCount: Int = 0,
    val loading: Boolean = false,
    val downloading: Boolean = false,
    val link: String = "",
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class SaverViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val workManager = WorkManager.getInstance(application)
    private val preferences = application.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val downloadId = MutableStateFlow(preferences.getString("id", null)?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    })
    private val mutableState = MutableStateFlow(SaverState(folderCount = repository.folders().size))
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            var previous: WorkInfo? = null
            downloadId.flatMapLatest { id ->
                if (id == null) flowOf(null) else workManager.getWorkInfoByIdFlow(id)
            }.collect { latest ->
                val active = latest != null && !latest.state.isFinished
                mutableState.update { it.copy(downloading = active) }
                if (latest?.state?.isFinished == true && latest != previous &&
                    preferences.getString("reported", null) != latest.id.toString()) {
                    if (latest.state != WorkInfo.State.CANCELLED) {
                        showMessage(latest.outputData.getString(DownloadWorker.MESSAGE_KEY) ?: "Download finished.")
                    }
                    refresh()
                    preferences.edit { putString("reported", latest.id.toString()) }
                }
                previous = latest
            }
        }
    }

    fun setLink(value: String) { mutableState.update { it.copy(link = value) } }
    fun showMessage(value: String) { mutableState.update { it.copy(message = value) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = operation {
            mutableState.update { it.copy(loading = true, folderCount = repository.folders().size) }
            try {
                val saved = repository.saved()
                mutableState.update { it.copy(saved = saved) }
                // Do not retain stale source items if a folder grant has expired.
                mutableState.update { it.copy(statuses = emptyList()) }
                val statuses = repository.statuses()
                mutableState.update { it.copy(statuses = statuses) }
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }

    fun connectFolder(uri: Uri) = operation {
        repository.connectFolder(uri)
        refresh()
    }

    fun disconnectFolders() = operation {
        refreshJob?.cancel()
        repository.disconnectFolders()
        mutableState.update { it.copy(statuses = emptyList(), folderCount = 0) }
    }

    fun importMedia(uris: List<Uri>) = operation {
        var count = 0
        var failed = 0
        for (uri in uris.distinct()) {
            try { repository.save(uri); count++ }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed++ }
        }
        showMessage(if (failed == 0) "Saved $count file(s)." else "Saved $count file(s); $failed could not be imported.")
        refresh()
    }

    fun save(item: MediaItem) = importMedia(listOf(item.uri))
    fun delete(item: MediaItem) = operation {
        repository.delete(item)
        refresh()
        showMessage("Media deleted.")
    }

    fun export(item: MediaItem, destination: Uri) = operation {
        repository.export(item, destination)
        showMessage("Media exported.")
    }

    fun download() = operation {
        if (state.value.downloading) return@operation
        val url = PublicMediaResolver.normalizeUrl(state.value.link)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.URL_KEY to url))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        mutableState.update { it.copy(downloading = true) }
        // REPLACE also handles a stale persisted ID after WorkManager has pruned history.
        workManager.enqueueUniqueWork(DownloadWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        preferences.edit { putString("id", request.id.toString()) }
        downloadId.value = request.id
        showMessage("Download queued. It will continue if you leave the app.")
    }

    fun cancelDownload() { workManager.cancelUniqueWork(DownloadWorker.WORK_NAME) }

    private fun operation(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showMessage(error.message ?: "Something went wrong. Try again.") }
    }
}
