package com.cd.statussaver.data

import android.net.Uri

data class MediaItem(val uri: Uri, val name: String, val mimeType: String, val saved: Boolean = false) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
