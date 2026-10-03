package me.rerere.highlight

import me.rerere.highlight.core.HighlightEngine
import me.rerere.highlight.languages.builtinLanguages

/** RikkaHub's pure Kotlin highlight.js 11.11.1 engine; see NOTICE.md. No UI or length cutoff. */
class CodeHighlighter {
    private val engine = HighlightEngine(builtinLanguages())

    fun supports(language: String): Boolean = engine.supports(language)

    fun highlight(code: String, language: String): List<HighlightToken> =
        if (code.isEmpty()) emptyList()
        else engine.highlight(code, language) ?: listOf(HighlightToken.Plain(code))
}
