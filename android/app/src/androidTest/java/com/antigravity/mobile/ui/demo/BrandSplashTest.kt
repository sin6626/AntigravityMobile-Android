package com.antigravity.mobile.ui.demo

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.antigravity.mobile.R
import org.junit.Assert.*
import org.junit.Test

class BrandSplashTest {
    @Test fun disabledSystemMotionSkipsTheLaunchAnimation() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("launchReducedMotion") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertFalse(ValueAnimator.areAnimatorsEnabled())
                val surface = FrameLayout(instrumentation.targetContext)
                val icon = ImageView(instrumentation.targetContext).apply { layout(0, 0, 288, 288) }
                var completed = false
                assertNull(animateBrandSplash(surface, icon) { completed = true })
                assertTrue(completed)
                assertEquals(1f, icon.alpha, 0f)
            }
        }
    }

    @Test fun launchMovesAndSweepsWithoutAnInitialJumpAndAlwaysReleasesTheOverlay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var surface: FrameLayout
            lateinit var icon: ImageView
            lateinit var animator: ValueAnimator
            var completions = 0
            scenario.onActivity { activity ->
                surface = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
                icon = ImageView(activity).apply { setImageResource(R.drawable.brand_splash_icon) }
                val size = (288 * activity.resources.displayMetrics.density).toInt()
                surface.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
                activity.setContentView(surface)
            }
            instrumentation.waitForIdleSync()
            fun frame(): IntArray {
                lateinit var pixels: IntArray
                scenario.onActivity {
                    val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
                    surface.draw(Canvas(bitmap))
                    pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    bitmap.recycle()
                }
                return pixels
            }
            val original = frame()
            scenario.onActivity {
                animator = checkNotNull(animateBrandSplash(surface, icon) { completions++ })
                animator.pause()
                animator.currentPlayTime = 0
            }
            val handoff = frame()
            val maxChannelDelta = original.indices.maxOf { i ->
                maxOf(kotlin.math.abs(Color.red(original[i]) - Color.red(handoff[i])),
                    kotlin.math.abs(Color.green(original[i]) - Color.green(handoff[i])),
                    kotlin.math.abs(Color.blue(original[i]) - Color.blue(handoff[i])))
            }
            // Capturing and compositing the icon can round premultiplied alpha by a few levels.
            assertTrue("系统图标交接首帧色差=$maxChannelDelta", maxChannelDelta <= 3)
            scenario.onActivity { animator.currentPlayTime = 260 }
            val moving = frame()
            assertTrue("图标需要实际发生扫光与浮起", original.indices.count { original[it] != moving[it] } > 100)
            scenario.onActivity { assertEquals(1f, surface.alpha, .001f); animator.currentPlayTime = 560 }
            scenario.onActivity { assertTrue(surface.alpha in .05f.. .95f); animator.currentPlayTime = 700 }
            val faded = frame()
            fun ink(pixels: IntArray) = pixels.sumOf { (255 - Color.red(it) + 255 - Color.green(it) + 255 - Color.blue(it)).toLong() }
            assertTrue("图标必须与背景一同淡出，不能末帧突然消失", ink(faded) < ink(original) / 10)
            scenario.onActivity { animator.end() }
            assertEquals(1, completions)
            scenario.onActivity { assertEquals(0f, surface.alpha, .001f); surface.alpha = 1f }
            assertArrayEquals("结束后必须清除覆盖图层并释放原图标", original, frame())
            scenario.onActivity {
                animator = checkNotNull(animateBrandSplash(surface, icon) { completions++ })
                animator.pause()
                animator.currentPlayTime = 200
                animator.cancel()
            }
            assertEquals("后台取消也必须清理", 2, completions)
            assertArrayEquals(original, frame())
        }
    }
}
