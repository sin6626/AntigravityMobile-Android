package com.antigravity.mobile.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputTest {
    @Test fun transcriptBecomesEditableDraftWithoutLosingExistingText() {
        assertEquals("帮我检查动画", voiceDraftWithTranscript("", " 帮我检查动画 "))
        assertEquals("已有草稿 继续补充", voiceDraftWithTranscript("已有草稿", "继续补充"))
        assertEquals("已有草稿\n继续补充", voiceDraftWithTranscript("已有草稿\n", "继续补充"))
        assertEquals("已有草稿", voiceDraftWithTranscript("已有草稿", " "))
    }
}
