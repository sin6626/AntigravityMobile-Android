package com.antigravity.mobile.data.service

internal fun safeDownloadedFilename(name: String): String {
    val clean = name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\u0000-\\u001F\\\\/:*?\"<>|]"), "_")
        .trim().trim('.')
    return clean.take(180).ifBlank { "文件" }
}

internal fun mimeTypeForFilename(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "md", "markdown", "mdown", "mkd" -> "text/markdown"
        "txt", "log", "kt", "kts", "java", "js", "ts", "py", "go", "rs", "c", "cpp", "h", "hpp", "sh", "ps1" -> "text/plain"
        "csv" -> "text/csv"
        "yaml", "yml" -> "text/yaml"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "zip" -> "application/zip"
        "gz" -> "application/gzip"
        "tar" -> "application/x-tar"
        else -> "application/octet-stream"
    }
}
