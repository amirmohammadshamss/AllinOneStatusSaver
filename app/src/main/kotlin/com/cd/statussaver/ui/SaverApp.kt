package com.cd.statussaver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cd.statussaver.R
import com.cd.statussaver.SaverState
import com.cd.statussaver.SaverViewModel
import com.cd.statussaver.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaverApp(model: SaverViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardEmpty = stringResource(R.string.clipboard_empty)
    var page by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<MediaItem?>(null) }
    var delete by remember { mutableStateOf<MediaItem?>(null) }
    val mediaSaver = listSaver<MediaItem?, String>(
        save = { item -> item?.let { listOf(it.uri.toString(), it.name, it.mimeType, it.saved.toString()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else MediaItem(it[0].toUri(), it[1], it[2], it[3].toBoolean()) }
    )
    var export by rememberSaveable(stateSaver = mediaSaver) { mutableStateOf<MediaItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(model::connectFolder)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.importMedia(uris)
    }
    val exportPicker = rememberLauncherForActivityResult(ExportMediaContract()) { uri ->
        val item = export
        if (uri != null && item != null) model.export(item, uri)
        export = null
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            model.dismissMessage()
        }
    }
    LaunchedEffect(state.link) { if (state.link.isNotBlank()) page = 1 }

    SaverTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) }) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    listOf(stringResource(R.string.tab_statuses), stringResource(R.string.tab_links), stringResource(R.string.tab_saved), stringResource(R.string.tab_about)).forEachIndexed { index, label ->
                        NavigationBarItem(selected = page == index, onClick = { page = index },
                            icon = { Text(listOf("◉", "↗", "▣", "ⓘ")[index]) }, label = { Text(label) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).widthIn(max = 1000.dp)) {
                if (state.loading || state.downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (page) {
                    0, 2 -> {
                        val savedPage = page == 2
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (savedPage) stringResource(R.string.collection_title) else stringResource(R.string.statuses_title), style = MaterialTheme.typography.headlineSmall)
                            Text(if (savedPage) stringResource(R.string.collection_help)
                                else stringResource(R.string.statuses_help),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (savedPage) importPicker.launch(arrayOf("image/*", "video/*"))
                                    else folderPicker.launch(whatsappFolder())
                                }) { Text(if (savedPage) stringResource(R.string.import_media) else stringResource(R.string.connect_folder)) }
                                OutlinedButton(onClick = { model.refresh() }) { Text(stringResource(R.string.refresh)) }
                            }
                            if (!savedPage && state.folderCount > 0) {
                                TextButton(onClick = { model.disconnectFolders() }) { Text(stringResource(R.string.disconnect_folders, state.folderCount)) }
                            }
                            if (!savedPage) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { openApp(context, "com.whatsapp", model::showMessage) }) {
                                        Text(stringResource(R.string.open_whatsapp))
                                    }
                                    TextButton(onClick = { openApp(context, "com.whatsapp.w4b", model::showMessage) }) {
                                        Text(stringResource(R.string.open_business))
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(stringResource(R.string.filter_all), stringResource(R.string.filter_images), stringResource(R.string.filter_videos)).forEachIndexed { index, label ->
                                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                                }
                            }
                        }
                        val media = (if (savedPage) state.saved else state.statuses).filter {
                            filter == 0 || (filter == 1 && !it.isVideo) || (filter == 2 && it.isVideo)
                        }
                        if (media.isEmpty()) {
                            EmptyState(if (savedPage) stringResource(R.string.empty_saved_title) else stringResource(R.string.empty_statuses_title),
                                if (savedPage) stringResource(R.string.empty_saved_help)
                                else stringResource(R.string.empty_statuses_help))
                        } else {
                            LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(media, key = { it.uri.toString() }) { item ->
                                    MediaCard(item, onPreview = { preview = item }, onSave = { model.save(item) },
                                        onShare = { share(context, item, model::showMessage) },
                                        onExport = { export = item; exportPicker.launch(item) },
                                        onDelete = { delete = item })
                                }
                            }
                        }
                    }
                    1 -> LinkScreen(state, model::setLink, model::download, model::cancelDownload, {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (value.isNotBlank()) model.setLink(value) else model.showMessage(clipboardEmpty)
                    }, { importPicker.launch(arrayOf("image/*", "video/*")) })
                    3 -> AboutScreen()
                }
            }
        }
        preview?.let { item -> MediaPreview(item, onDismiss = { preview = null }, onError = model::showMessage) }
        delete?.let { item ->
            AlertDialog(onDismissRequest = { delete = null }, title = { Text(stringResource(R.string.delete_title)) },
                text = { Text(stringResource(R.string.delete_help)) },
                confirmButton = { TextButton(onClick = { model.delete(item); delete = null }) { Text(stringResource(R.string.delete)) } },
                dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
}

@Composable
private fun LinkScreen(state: SaverState, onLink: (String) -> Unit, onDownload: () -> Unit,
    onCancel: () -> Unit, onPaste: () -> Unit, onImport: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text(stringResource(R.string.link_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.link_help))
        }
        item {
            OutlinedTextField(value = state.link, onValueChange = onLink, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.https_link)) }, maxLines = 4)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPaste) { Text(stringResource(R.string.paste)) }
                Button(onClick = onDownload, enabled = state.link.isNotBlank() && !state.downloading) { Text(stringResource(R.string.download)) }
                if (state.downloading) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            if (state.downloading) Text(stringResource(R.string.download_running))
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.import_help))
                    OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_media)) }
                }
            }
        }
        item {
            Text(stringResource(R.string.social_links), style = MaterialTheme.typography.titleLarge)
            Text("Instagram · Facebook · TikTok · X / Twitter · Likee · ShareChat · Roposo · SnackVideo",
                modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.social_help),
                modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem, onPreview: () -> Unit, onSave: () -> Unit,
    onShare: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onPreview)) {
            MediaThumbnail(item, Modifier.fillMaxSize())
            if (item.isVideo) Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.video_badge), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { menu = true }) { Text("•••") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.preview)) }, onClick = { menu = false; onPreview() })
                    if (!item.saved) DropdownMenuItem(text = { Text(stringResource(R.string.save)) }, onClick = { menu = false; onSave() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export)) }, onClick = { menu = false; onExport() })
                    if (item.saved) DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutScreen() {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(listOf(
            R.string.about_title to R.string.about_description,
            R.string.getting_started to R.string.getting_started_help,
            R.string.your_files to R.string.your_files_help,
            R.string.privacy to R.string.privacy_help,
            R.string.permissions to R.string.permissions_help
        )) { (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaItem, modifier: Modifier = Modifier, fullSize: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, item.uri, fullSize) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (item.isVideo) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, item.uri)
                        if (android.os.Build.VERSION.SDK_INT >= 27)
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
                        else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally { retriever.release() }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val size = if (fullSize) 1600 else 512
                    var sample = 1
                    while (bounds.outWidth / sample > size * 2 || bounds.outHeight / sample > size * 2) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), contentDescription = item.name, modifier = modifier,
        contentScale = if (fullSize) ContentScale.Fit else ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(if (item.isVideo) stringResource(R.string.video) else stringResource(R.string.image))
    }
}

@Composable
private fun MediaPreview(item: MediaItem, onDismiss: () -> Unit, onError: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                if (item.isVideo) AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { context -> VideoView(context).apply {
                        setVideoURI(item.uri)
                        setMediaController(MediaController(context).also { it.setAnchorView(this) })
                        setOnPreparedListener { start() }
                        setOnErrorListener { _, _, _ -> onError(context.getString(R.string.video_error)); true }
                    } },
                    onRelease = { it.stopPlayback() }
                ) else MediaThumbnail(item, Modifier.fillMaxWidth().weight(1f), fullSize = true)
            }
        }
    }
}

private fun whatsappFolder(): Uri = (
    "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia"
).toUri()

private fun share(context: Context, item: MediaItem, onError: (String) -> Unit) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            clipData = ClipData.newRawUri(item.name, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_media)))
    } catch (_: Exception) { onError(context.getString(R.string.share_error)) }
}

private fun openApp(context: Context, packageName: String, onError: (String) -> Unit) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) onError(context.getString(R.string.app_unavailable))
        else context.startActivity(intent)
    } catch (_: Exception) { onError(context.getString(R.string.app_unavailable)) }
}
