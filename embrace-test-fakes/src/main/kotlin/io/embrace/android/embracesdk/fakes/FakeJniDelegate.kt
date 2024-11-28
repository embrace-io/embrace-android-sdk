package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate

class FakeJniDelegate : JniDelegate {

    var crashRaw: String? = null
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

    override fun updateMetaData(metadata: String?) {
        // do nothing
    }

    override fun onSessionChange(sessionId: String?, reportPath: String) {
        this.reportPath = reportPath
    }

    override fun updateAppState(appState: String?) {
        // do nothing
    }

    override fun getCrashReport(path: String?): String? {
        return crashRaw
    }

    override fun checkForOverwrittenHandlers(): String? {
        return culprit
    }

    override fun reinstallSignalHandlers(): Boolean {
        signalHandlerReinstalled = true
        return false
    }
}
