package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni

class JniDelegateImpl : JniDelegate {
    external override fun installSignalHandlers(
        markerFilePath: String?,
        appState: String?,
        reportId: String?,
        apiLevel: Int,
        is32bit: Boolean,
        devLogging: Boolean,
    )

    external override fun onSessionChange(sessionId: String, reportPath: String)
    external override fun getCrashReport(path: String): String?
    external override fun checkForOverwrittenHandlers(): String?
    external override fun reinstallSignalHandlers(): Boolean
}
