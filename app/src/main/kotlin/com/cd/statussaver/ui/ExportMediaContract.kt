package com.cd.statussaver.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.cd.statussaver.data.MediaItem

/** Pass the MIME as input so launching immediately never uses a stale Compose state. */
class ExportMediaContract : ActivityResultContract<MediaItem, Uri?>() {
    override fun createIntent(context: Context, input: MediaItem): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.mimeType
            putExtra(Intent.EXTRA_TITLE, input.name)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
