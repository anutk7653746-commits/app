package com.findora.app

enum class SearchMode {
    ALL, IMAGES, VIDEOS
}

data class MediaItem(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val sourcePageUrl: String,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val licenseName: String,
    val licenseUrl: String?,
    val mimeType: String,
    val sizeBytes: Long?,
    val fileName: String
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
