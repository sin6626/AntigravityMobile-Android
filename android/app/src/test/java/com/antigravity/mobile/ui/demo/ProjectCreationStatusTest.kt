package com.antigravity.mobile.ui.demo

import com.antigravity.mobile.ui.chat.ChatUiState
import com.antigravity.mobile.data.model.ChatModel
import org.junit.Assert.*
import org.junit.Test

class ProjectCreationStatusTest {
    @Test fun unavailableModelStatesHaveSpecificFeedback() {
        assertEquals("暂无可用模型", ChatUiState().projectCreationUnavailableReason())
        assertEquals("模型加载失败", ChatUiState(modelsError = "offline").projectCreationUnavailableReason())
        assertEquals("正在加载模型…", ChatUiState(isLoadingModels = true, modelsError = "offline").projectCreationUnavailableReason())
    }

    @Test fun busyStatesWinOverCatalogStateAndAvailableModelsEnableCreation() {
        assertEquals("正在创建会话…", ChatUiState(creatingProjectKey = "p", modelsError = "offline").projectCreationUnavailableReason())
        assertEquals("正在回退，请稍候", ChatUiState(isReverting = true).projectCreationUnavailableReason())
        assertEquals("正在发送，请稍候", ChatUiState(isSending = true).projectCreationUnavailableReason())
        val ready = ChatUiState(models = listOf(ChatModel("m", "Gemini", "MODEL_M", true)))
        assertNull(ready.projectCreationUnavailableReason())
        assertEquals("正在刷新模型…", ready.copy(isLoadingModels = true).projectCreationUnavailableReason())
    }
}
