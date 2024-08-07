package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.crash.CrashFileMarker

public class FakeCrashFileMarker : CrashFileMarker {

    public var marked: Boolean = false

    override fun mark() {
        marked = true
    }

    override fun removeMark() {
        marked = false
    }

    override fun isMarked(): Boolean {
        return marked
    }

    override fun getAndCleanMarker(): Boolean {
        return marked.also { marked = false }
    }

    override fun handleCrash(crashId: String) {
        mark()
    }
}
