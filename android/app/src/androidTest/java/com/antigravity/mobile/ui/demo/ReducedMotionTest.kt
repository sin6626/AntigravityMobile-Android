package com.antigravity.mobile.ui.demo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ReducedMotionTest {
    private val scale = object : MotionDurationScale {
        var value = mutableFloatStateOf(1f)
        override val scaleFactor get() = value.floatValue
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = scale)

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
        compose.onNodeWithText("图片文件").assertIsDisplayed().performClick()
        org.junit.Assert.assertTrue(selected)
        compose.onNodeWithText("图片文件").assertDoesNotExist()
    }

    @Test fun disablingMotionFinishesPendingStreamWithoutAnotherServerUpdate() {
        val source = mutableStateOf("开始")
        compose.setContent { StreamingMessageText("live", source.value) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("开始").fetchSemanticsNodes().isNotEmpty() }
        val full = "开始" + "待显示文字".repeat(350)
        compose.runOnIdle { source.value = full }
        compose.waitForIdle()
        compose.onNodeWithText(full).assertDoesNotExist()
        compose.runOnIdle { scale.value.floatValue = 0f }
        compose.waitUntil(1_000) { compose.onAllNodesWithText(full).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(full).assertExists()
        compose.runOnIdle { source.value += "。" }
        compose.onNodeWithText(full + "。").assertExists()
    }
}
