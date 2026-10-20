package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate

class FakeJniDelegate : JniDelegate {

    private val rawCrashes: MutableMap<String, String?> = mutableMapOf()
    var culprit: String? = "testCulprit"
    var sessionPartId: String? = null
    var userSessionId: String? = null
    var reportPath: String? = null
    var signalHandlerInstalled: Boolean = false
    var signalHandlerReinstalled = false
    var crashCaptureDisabled = false
    var installError: Throwable? = null
    var checkError: Throwable? = null

    override fun installSignalHandlers(
        markerFilePath: String?,
        reportId: String?,
        devLogging: Boolean,
    ) {
        installError?.let { throw it }
        signalHandlerInstalled = true
    }

    override fun onSessionChange(sessionPartId: String, userSessionId: String, reportPath: String) {
        this.sessionPartId = sessionPartId
        this.userSessionId = userSessionId
        this.reportPath = reportPath
    }

    fun addCrashRaw(path: String, raw: String?) {
        rawCrashes[path] = raw
    }

    override fun getCrashReport(path: String): String? {
        return rawCrashes[path]
    }

    override fun checkForOverwrittenHandlers(): String? {
        checkError?.let { throw it }
        return culprit
    }

    override fun reinstallSignalHandlers(): Boolean {
        signalHandlerReinstalled = true
        return false
    }

    override fun disableCrashCapture() {
        crashCaptureDisabled = true
    }
}
