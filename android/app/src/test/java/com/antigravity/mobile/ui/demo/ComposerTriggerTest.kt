package com.antigravity.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerTriggerTest {
    @Test fun findsOnlyTrailingStandaloneContextTriggers() {
        assertEquals(ComposerTrigger('@', "Chat", 5), composerTrigger("检查一下 @Chat"))
        assertEquals(ComposerTrigger('/', "plan", 0), composerTrigger("/plan"))
        assertNull(composerTrigger("mail@example.com"))
        assertNull(composerTrigger("/plan 后续说明"))
    }

    @Test fun removesOnlyTheActiveToken() {
        assertEquals("检查一下 ", removeComposerTrigger("检查一下 @Chat"))
        assertEquals("", removeComposerTrigger("/plan"))
    }
}
