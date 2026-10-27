package com.example.tvviewer

import java.io.File

enum class ExplorerEntryType {
    PARENT_DIR,
    DIRECTORY,
    ZIP_PACKAGE,
    HTML_PAGE
}

data class ExplorerEntry(
    val file: File,
    val type: ExplorerEntryType,
    val displayTitle: String,
    val tagLabel: String,
    val sizeLabel: String
)

data class StreamLogItem(
    val relativePath: String,
    val mimeType: String,
    val success: Boolean
)
