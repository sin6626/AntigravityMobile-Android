package com.antigravity.mobile.ui.demo

import java.text.BreakIterator
import java.util.Locale

/** Keeps stream presentation separate from the gateway's authoritative text. */
internal class StreamingTextReveal(initialText: String = "") {
    private var source = initialText
    var shown: String = initialText
        private set
    private var pending = emptyList<String>()
    private var offset = 0
    private var lastInputAt = 0L
    private var lastAdvanceAt = 0L
    private var arrivalRate = 38.0

    val hasPending: Boolean get() = offset < pending.size

    fun receive(text: String, animate: Boolean, atMs: Long) {
        if (!animate || !text.startsWith(source)) {
            source = text
            finish()
            lastInputAt = atMs
            arrivalRate = 38.0
            return
        }
        if (text == source) return
        val added = graphemes(text.substring(source.length))
        if (lastInputAt > 0L) {
            val rate = added.size * 1000.0 / (atMs - lastInputAt).coerceAtLeast(16L)
            arrivalRate += (rate - arrivalRate) * 0.35
        }
        if (!hasPending) lastAdvanceAt = atMs
        pending = pending.drop(offset) + added
        offset = 0
        source = text
        lastInputAt = atMs
    }

    fun advance(atMs: Long) {
        if (!hasPending) return
        val elapsedMs = (atMs - lastAdvanceAt).coerceIn(0L, 120L)
        if (elapsedMs == 0L) return
        lastAdvanceAt = atMs
        val backlog = pending.size - offset
        val speed = maxOf(38.0, arrivalRate * 1.1, backlog / 0.18)
        val batch = if (atMs - lastInputAt >= 450L || backlog > 2048) backlog
            else (speed * elapsedMs / 1000.0).toInt().coerceAtLeast(1)
        val end = (offset + batch).coerceAtMost(pending.size)
        shown += pending.subList(offset, end).joinToString("")
        offset = end
        if (!hasPending) finish()
    }

    fun finish() {
        shown = source
        pending = emptyList()
        offset = 0
    }

    private fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val result = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }
}
