package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate

class FakeJniDelegate : JniDelegate {

    var culprit: String? = "testCulprit"
    var signalHandlerInstalled: Boolean = false
    var signalHandlerReinstalled = false

    override fun installSignalHandlers(
        reportPath: String?,
        markerFilePath: String?,
        sessionId: String?,
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

    override fun updateSessionId(sessionId: String?) {
        // do nothing
    }

    override fun updateAppState(appState: String?) {
        // do nothing
    }

    override fun getCrashReport(path: String?): String? {
        return null
    }

    override fun getErrors(path: String?): String? {
        return null
    }

    override fun checkForOverwrittenHandlers(): String? {
        return culprit
    }

    override fun reinstallSignalHandlers(): Boolean {
        signalHandlerReinstalled = true
        return false
    }
}
