package com.antigravity.mobile.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FileDownloadSupportTest {
    @Test fun downloadedNamesCannotEscapeCacheAndKeepUsefulExtension() {
        assertEquals("报告_最终版.pdf", safeDownloadedFilename("../报告:最终版.pdf"))
        assertEquals("文件", safeDownloadedFilename(".."))
    }

    @Test fun desktopDocumentAndMediaTypesUseSystemCompatibleMimeTypes() {
        assertEquals("application/pdf", mimeTypeForFilename("plan.PDF"))
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            mimeTypeForFilename("review.docx"))
        assertEquals("video/mp4", mimeTypeForFilename("recording.mp4"))
        assertEquals("application/octet-stream", mimeTypeForFilename("artifact.unknown_type"))
    }
}
