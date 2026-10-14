package com.antigravity.mobile.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputTest {
    @Test fun onlyActualScopeRejectionClaimsTheAccountIsNotEnabled() {
        assertEquals("当前电脑账号未开放语音转写", voiceErrorMessage(
            java.io.IOException("PermissionDenied: insufficient authentication scopes")))
        assertEquals("电脑语音服务拒绝访问，请检查授权", voiceErrorMessage(
            java.io.IOException("PermissionDenied: service policy")))
        assertEquals("语音连接超时，请重试", voiceErrorMessage(java.net.SocketTimeoutException()))
        assertEquals("语音转写失败，请检查电脑端连接后重试", voiceErrorMessage(java.io.IOException("broken connection")))
    }
    @Test fun transcriptBecomesEditableDraftWithoutLosingExistingText() {
        assertEquals("帮我检查动画", voiceDraftWithTranscript("", " 帮我检查动画 "))
        assertEquals("已有草稿 继续补充", voiceDraftWithTranscript("已有草稿", "继续补充"))
        assertEquals("已有草稿\n继续补充", voiceDraftWithTranscript("已有草稿\n", "继续补充"))
        assertEquals("已有草稿", voiceDraftWithTranscript("已有草稿", " "))
    }
}
