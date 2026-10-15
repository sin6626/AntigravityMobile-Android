package com.antigravity.mobile.ui.demo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/** Animate the actual system icon in place so there is no second splash or alignment jump. */
internal fun animateBrandSplash(surface: View, icon: View, finished: () -> Unit): ValueAnimator? {
    if (surface !is ViewGroup || icon.width == 0 || icon.height == 0 || !ValueAnimator.areAnimatorsEnabled()) {
        finished()
        return null
    }
    val bitmap = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
    icon.draw(Canvas(bitmap))
    val origin = IntArray(2).also(surface::getLocationOnScreen)
    val position = IntArray(2).also(icon::getLocationOnScreen)
    val mark = LaunchMark(surface.context, bitmap)
    mark.layout(position[0] - origin[0], position[1] - origin[1],
        position[0] - origin[0] + icon.width, position[1] - origin[1] + icon.height)
    surface.overlay.add(mark)
    icon.alpha = 0f
    return ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 760
        interpolator = LinearInterpolator()
        addUpdateListener {
            val progress = it.animatedValue as Float
            mark.progress = progress
            mark.invalidate()
            val settle = progress * progress * (3f - 2f * progress)
            mark.translationY = -8f * surface.resources.displayMetrics.density * settle
            mark.scaleX = 1f + .045f * sin(progress * PI.toFloat())
            mark.scaleY = mark.scaleX
            val exit = ((progress - .48f) / .52f).coerceIn(0f, 1f)
            val alpha = 1f - exit * exit * (3f - 2f * exit)
            surface.alpha = alpha
            // A view owns a RenderNode, so its opacity also updates on hardware-rendered splash layers.
            mark.alpha = alpha
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                surface.overlay.remove(mark)
                icon.alpha = 1f
                bitmap.recycle()
                finished()
            }
        })
        start()
    }
}

private class LaunchMark(context: Context, private val bitmap: Bitmap) : View(context) {
    var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val sheen = LinearGradient(0f, 0f, bitmap.width * .65f, bitmap.height * .15f,
        intArrayOf(Color.TRANSPARENT, Color.argb(150, 224, 247, 255), Color.TRANSPARENT),
        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = sheen
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The highlight is clipped to the generated mark's alpha, never to a rectangular card.
        val layer = canvas.saveLayer(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), null)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        val sweep = ((progress - .06f) / .66f).coerceIn(0f, 1f)
        matrix.setTranslate(bitmap.width * (sweep * 1.65f - .65f), 0f)
        sheen.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), sheenPaint)
        canvas.restoreToCount(layer)
    }
}
