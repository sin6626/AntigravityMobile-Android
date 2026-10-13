package com.antigravity.mobile.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RunningTasksPanelTest {
    private val start = Instant.parse("2026-10-13T08:00:00Z").toEpochMilli()

    @Test fun formatsElapsedTimeWithoutNegativeValues() {
        assertEquals("0秒", runningTaskDuration("2026-10-13T08:00:00Z", start - 1_000))
        assertEquals("2分5秒", runningTaskDuration("2026-10-13T08:00:00Z", start + 125_000))
        assertEquals("1小时2分", runningTaskDuration("2026-10-13T08:00:00Z", start + 3_725_000))
        assertEquals("计时中", runningTaskDuration("invalid", start))
    }
}
