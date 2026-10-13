package com.antigravity.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopStatusTest {
    @Test fun permissionPresetUsesDesktopTerms() {
        assertEquals("请求审阅", permissionPresetLabel("REQUEST_REVIEW"))
        assertEquals("Turbo", permissionPresetLabel("TURBO"))
        assertEquals("默认", permissionPresetLabel("UNSPECIFIED"))
    }
}
