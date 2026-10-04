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
