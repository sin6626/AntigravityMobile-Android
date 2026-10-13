package com.antigravity.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ModelQuotaTest {
    @Test fun resetTimeShowsRelativeAndLocalClock() {
        val text = formatQuotaResetTime(
            "2026-10-13T06:31:00Z",
            Instant.parse("2026-10-13T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )
        assertEquals("2 小时 31 分钟后恢复 · 14:31", text)
        assertEquals("即将恢复 · 11:59", formatQuotaResetTime(
            "2026-10-13T03:59:00Z",
            Instant.parse("2026-10-13T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        ))
        assertNull(formatQuotaResetTime("not-a-time"))
    }
}
