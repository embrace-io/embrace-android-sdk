package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.crash.CrashFileMarker

internal class FakeCrashFileMarker : CrashFileMarker {

    var isMarked = false

    override fun mark() {
        isMarked = true
    }

    override fun removeMark() {
        isMarked = false
    }

    override fun isMarked(): Boolean {
        return isMarked
    }

    override fun getAndCleanMarker(): Boolean {
        return isMarked.also { isMarked = false }
    }
}
