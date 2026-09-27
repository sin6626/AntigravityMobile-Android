package com.antigravity.mobile

import android.app.Application
import com.didichuxing.doraemonkit.DoraemonKit

class DebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DoraemonKit.install(this)
    }
}
