package com.antigravity.mobile.data.service

internal fun imageAttachmentName(clientMessageId: String, index: Int): String =
    "multigravity-$clientMessageId-$index"
