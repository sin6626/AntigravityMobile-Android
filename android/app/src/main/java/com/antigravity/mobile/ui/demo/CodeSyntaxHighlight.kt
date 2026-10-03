package com.antigravity.mobile.ui.demo

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.highlight.CodeHighlighter
import me.rerere.highlight.HighlightToken

private data class CodeKey(val code: String, val language: String)

private val highlightCache by lazy {
    object : LruCache<CodeKey, AnnotatedString>(2 * 1024 * 1024) {
        // Approximate payload budget; bounded across completed blocks, not an entry-count-only cache.
        override fun sizeOf(key: CodeKey, value: AnnotatedString): Int =
            maxOf(1, key.code.length * 4 + value.spanStyles.size * 48)
    }
}
private val highlightMutex = Mutex()
private val codeHighlighter by lazy { CodeHighlighter() }

@Composable
internal fun rememberHighlightedCode(code: String, info: String): AnnotatedString {
    val language = remember(info) { info.trim().substringBefore(' ').lowercase() }
    val key = remember(code, language) { CodeKey(code, language) }
    var highlighted by remember(key) { mutableStateOf(highlightCache.get(key) ?: AnnotatedString(code)) }
    LaunchedEffect(key) {
        highlighted = withContext(Dispatchers.Default) {
            highlightMutex.withLock {
                ensureActive()
                highlightCache.get(key) ?: colorizeCode(codeHighlighter.highlight(code, language)).also {
                    ensureActive()
                    highlightCache.put(key, it)
                }
            }
        }
    }
    return highlighted
}

internal fun colorizeCode(tokens: List<HighlightToken>): AnnotatedString = buildAnnotatedString {
    tokens.forEach { token ->
        if (token is HighlightToken.Styled) withStyle(SpanStyle(color = codeScopeColor(token.type))) {
            append(token.content)
        } else append(token.content)
    }
}

// Colors only: font metrics, selection and scroll positions stay unchanged when results arrive.
private fun codeScopeColor(type: String): Color = when (type.substringBefore('.')) {
    "keyword", "doctag", "formula" -> Color(0xFF8250A6)
    "string", "regexp", "addition", "attribute" -> Color(0xFF116329)
    "number", "literal", "type", "char", "attr", "selector-class", "selector-attr", "selector-pseudo" -> Color(0xFF953800)
    "comment", "quote" -> Color(0xFF57606A)
    "title", "function", "symbol", "built_in", "meta", "link", "selector-id" -> Color(0xFF0550AE)
    "section", "name", "tag", "selector-tag", "deletion", "property", "variable", "template-variable" -> Color(0xFF9A2440)
    else -> Color(0xFF24292F)
}
