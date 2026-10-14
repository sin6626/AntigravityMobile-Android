package com.antigravity.mobile.ui.demo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.antigravity.mobile.data.model.RunningTaskItem
import com.antigravity.mobile.ui.chat.RunningTasksPanel
import org.junit.Rule
import org.junit.Test

class ReducedMotionTest {
    private val scale = object : MotionDurationScale {
        var value = mutableFloatStateOf(1f)
        override val scaleFactor get() = value.floatValue
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = scale)

    @Test fun composerLayoutRemainsUsableWithMotionDisabled() {
        var clearFocus: () -> Unit = {}
        compose.runOnIdle { scale.value.floatValue = 0f }
        compose.setContent {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            clearFocus = { focus.clearFocus() }
            Composer(activeConversation = true, draft = "", attachments = emptyList(), onDraftChange = {},
                onRemoveImage = {}, onSend = {}, isSending = false, onUnsupported = {},
                modelBadge = "Gemini 3.8 Flash 高", workModeLabel = "直接")
        }
        compose.onNode(hasSetTextAction()).performClick().assertIsFocused()
        compose.onNodeWithContentDescription("选择模型").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("发送消息").assertCountEquals(1)
        val button = compose.onNodeWithContentDescription("发送消息").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(button.width, button.height, 1f)
        compose.runOnUiThread { clearFocus() }
        compose.onNodeWithContentDescription("选择模型").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test fun imageZoomAndResetRemainUsableWithMotionDisabled() {
        val bitmap = android.graphics.Bitmap.createBitmap(240, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val loader = coil.ImageLoader(compose.activity)
        try {
            compose.runOnIdle { scale.value.floatValue = 0f }
            compose.setContent { ZoomableImage(bitmap, loader, "无动画图片", androidx.compose.ui.Modifier) }
            val image = compose.onNodeWithContentDescription("无动画图片")
            compose.waitUntil(5_000) { image.fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }.startsWith("已加载") }
            image.performTouchInput { doubleClick(center) }
            image.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已加载，缩放 250%"))
            val reset = image.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
                .single { it.label == "恢复图片大小" }
            compose.runOnUiThread { reset.action() }
            image.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已加载，缩放 100%"))
        } finally { loader.shutdown() }
    }

    @Test fun attachmentMenuStillOpensAndDismissesWithMotionDisabled() {
        val open = mutableStateOf(true)
        var selected = false
        compose.runOnIdle { scale.value.floatValue = 0f }
        compose.setContent { ComposerPlusMenu(open.value, { open.value = false }, {}, {}, { selected = true; open.value = false }) }
        compose.onNodeWithText("文件").assertIsDisplayed().performClick()
        org.junit.Assert.assertTrue(selected)
        compose.onNodeWithText("文件").assertDoesNotExist()
    }

    @Test fun streamingMarkdownStaysCurrentWhenMotionIsDisabled() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.antigravity.mobile.ui.chat.ChatViewModel::class.java]
        val source = mutableStateOf("# 开始\n\n**实时正文**")
        compose.setContent {
            ConversationContent(vm, listOf(com.antigravity.mobile.data.model.GatewayMessageItem(
                id = "live", type = "agent", text = source.value)), "live", true, false, false, false, {})
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("实时正文").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { scale.value.floatValue = 0f }
        compose.runOnIdle { source.value += "\n\n**新增正文**" }
        compose.waitUntil(1_000) { compose.onAllNodesWithText("新增正文").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("新增正文").assertIsDisplayed()
        compose.onNodeWithText("正在回复…").assertIsDisplayed()
    }

    @Test fun runningTasksRemainUsableWithMotionDisabled() {
        var stopped = ""
        compose.runOnIdle { scale.value.floatValue = 0f }
        compose.setContent {
            RunningTasksPanel(
                tasks = listOf(RunningTaskItem(id = "task-1", stepIndex = 1, toolSummary = "检查界面", commandLine = "run ui")),
                stoppingIds = emptySet(),
                error = null,
                onStop = { stopped = it.id },
            )
        }
        compose.onNodeWithContentDescription("收起后台任务").performClick()
        compose.onNodeWithText("检查界面").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开后台任务").performClick()
        compose.onNodeWithContentDescription("停止后台任务 检查界面").performClick()
        org.junit.Assert.assertEquals("task-1", stopped)
    }
}
