package com.antigravity.mobile.ui.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.mermaid.kotlin.MermaidKotlin
import com.mermaid.kotlin.layout.LayoutConfig
import io.ratex.RaTeXEngine
import io.ratex.RaTeXFontLoader
import io.ratex.RaTeXRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private val richBitmapCache = object : LruCache<String, Bitmap>(32 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

@Composable
internal fun rememberMathBitmap(expression: String, fontSizePx: Float, display: Boolean): Bitmap? {
    val context = LocalContext.current.applicationContext
    val key = "math:$display:$fontSizePx:$expression"
    val bitmap by produceState<Bitmap?>(initialValue = richBitmapCache.get(key), key1 = key) {
        if (value == null) value = withContext(Dispatchers.Default) {
            richBitmapCache.get(key) ?: runCatching {
                renderMath(context, expression, fontSizePx, display)
            }.onFailure { Log.w("NativeRichMedia", "Math render failed: ${it.message}") }
                .getOrNull()?.also { richBitmapCache.put(key, it) }
        }
    }
    return bitmap
}

@Composable
internal fun rememberMermaidBitmap(source: String): Bitmap? {
    val key = "mermaid:$source"
    val bitmap by produceState<Bitmap?>(initialValue = richBitmapCache.get(key), key1 = key) {
        if (value == null) value = withContext(Dispatchers.Default) {
            richBitmapCache.get(key) ?: runCatching {
                MermaidKotlin(LayoutConfig(nodeWidth = 300f, nodeHeight = 70f, participantWidth = 180f, participantSpacing = 90f, stateWidth = 240f)).render(source)
            }
                .onFailure { Log.w("NativeRichMedia", "Mermaid render failed: ${it.message}") }
                .getOrNull()?.also { richBitmapCache.put(key, it) }
        }
    }
    return bitmap
}

private fun renderMath(context: Context, expression: String, fontSizePx: Float, display: Boolean): Bitmap {
    RaTeXFontLoader.ensureLoaded(context)
    val layout = RaTeXEngine.parseBlocking(expression, displayMode = display)
    val renderer = RaTeXRenderer(layout, fontSizePx, RaTeXFontLoader::getTypeface)
    val width = ceil(renderer.widthPx).toInt().coerceAtLeast(1)
    val height = ceil(renderer.totalHeightPx).toInt().coerceAtLeast(1)
    require(width <= 8192 && height <= 4096) { "Formula exceeds bitmap limit" }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        renderer.draw(Canvas(it))
    }
}

internal data class InlineMathRange(val start: Int, val endExclusive: Int, val expression: String)

internal fun findInlineMath(value: AnnotatedString): List<InlineMathRange> {
    val text = value.text
    fun validDollar(index: Int): Boolean = index in text.indices && text[index] == '$' &&
        value.getStringAnnotations("ESCAPED_DOLLAR", index, index + 1).isEmpty() &&
        value.spanStyles.none { index >= it.start && index < it.end && it.item.fontFamily == FontFamily.Monospace }
    val result = ArrayList<InlineMathRange>()
    var cursor = 0
    while (cursor < text.length) {
        val start = text.indexOf('$', cursor)
        if (start < 0) break
        if (!validDollar(start) || validDollar(start - 1) || validDollar(start + 1)) {
            cursor = start + 1
            continue
        }
        var end = text.indexOf('$', start + 1)
        while (end >= 0 && !validDollar(end)) end = text.indexOf('$', end + 1)
        if (end < 0 || text.substring(start + 1, end).contains('\n')) break
        if (end > start + 1) result.add(InlineMathRange(start, end + 1, text.substring(start + 1, end)))
        cursor = end + 1
    }
    return result
}
