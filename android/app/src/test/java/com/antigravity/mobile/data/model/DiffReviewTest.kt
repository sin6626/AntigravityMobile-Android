package com.antigravity.mobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffReviewTest {
    @Test fun diffKeepsBothLineNumbersAndCollapsesDistantContext() {
        val original = (1..20).joinToString("\n") { "line $it" }
        val modified = (1..20).joinToString("\n") { if (it == 10) "changed 10" else "line $it" }
        val rows = buildReviewDiff(original, modified)

        assertTrue(rows.any { it.kind == ReviewDiffLineKind.REMOVED && it.originalLine == 10 && it.text == "line 10" })
        assertTrue(rows.any { it.kind == ReviewDiffLineKind.ADDED && it.modifiedLine == 10 && it.text == "changed 10" })
        assertTrue(rows.indexOfFirst { it.kind == ReviewDiffLineKind.REMOVED } <
            rows.indexOfFirst { it.kind == ReviewDiffLineKind.ADDED })
        assertTrue(rows.any { it.kind == ReviewDiffLineKind.COLLAPSED })
    }

    @Test fun newFileUsesModifiedSideLineNumbers() {
        val rows = buildReviewDiff("", "first\nsecond")
        assertEquals(listOf(1, 2), rows.mapNotNull { it.modifiedLine })
        assertTrue(rows.all { it.kind == ReviewDiffLineKind.ADDED })
    }
}
