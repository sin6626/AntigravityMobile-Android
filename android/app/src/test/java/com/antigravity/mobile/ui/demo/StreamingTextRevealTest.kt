package com.antigravity.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTextRevealTest {
    @Test fun appendedReplyRevealsAndFinishesWithoutLosingCharacters() {
        val reveal = StreamingTextReveal()
        reveal.receive("你好 👩🏽‍💻 café", animate = true, atMs = 1000)
        assertTrue(reveal.hasPending)
        reveal.advance(1016)
        assertTrue("你好 👩🏽‍💻 café".startsWith(reveal.shown))
        reveal.advance(1600)
        assertEquals("你好 👩🏽‍💻 café", reveal.shown)
        assertFalse(reveal.hasPending)
    }

    @Test fun authoritativeCorrectionReplacesPendingText() {
        val reveal = StreamingTextReveal()
        reveal.receive("Initial", animate = false, atMs = 1000)
        reveal.receive("Initial appended", animate = true, atMs = 1100)
        reveal.receive("Corrected", animate = true, atMs = 1110)
        assertEquals("Corrected", reveal.shown)
        assertFalse(reveal.hasPending)
    }

    @Test fun largeBacklogCatchesUpImmediately() {
        val text = "文字".repeat(2100)
        val reveal = StreamingTextReveal()
        reveal.receive(text, animate = true, atMs = 1000)
        reveal.advance(1016)
        assertEquals(text, reveal.shown)
    }
}
