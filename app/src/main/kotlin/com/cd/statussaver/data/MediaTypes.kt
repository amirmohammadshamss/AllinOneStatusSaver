package com.cd.statussaver.data

import java.util.Locale

object MediaTypes {
    fun extension(mime: String): String? = when (mime.substringBefore(';').trim().lowercase(Locale.ROOT)) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        else -> null
    }

    fun fromName(name: String): String? = when (name.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        else -> null
    }
}
