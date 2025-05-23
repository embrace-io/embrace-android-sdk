package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate

class FakeJniDelegate : JniDelegate {

    private val rawCrashes: MutableMap<String, String?> = mutableMapOf()
    var culprit: String? = "testCulprit"
    var reportPath: String? = null
    var signalHandlerInstalled: Boolean = false
    var signalHandlerReinstalled = false

    override fun installSignalHandlers(
        markerFilePath: String?,
        appState: String?,
        reportId: String?,
        apiLevel: Int,
        is32bit: Boolean,
        devLogging: Boolean,
    ) {
        signalHandlerInstalled = true
    }

    override fun onSessionChange(sessionId: String, reportPath: String) {
        this.reportPath = reportPath
    }

    fun addCrashRaw(path: String, raw: String?) {
        rawCrashes[path] = raw
    }

    override fun getCrashReport(path: String): String? {
        return rawCrashes[path]
    }

    override fun checkForOverwrittenHandlers(): String? {
        return culprit
    }

    override fun reinstallSignalHandlers(): Boolean {
        signalHandlerReinstalled = true
        return false
    }
}
