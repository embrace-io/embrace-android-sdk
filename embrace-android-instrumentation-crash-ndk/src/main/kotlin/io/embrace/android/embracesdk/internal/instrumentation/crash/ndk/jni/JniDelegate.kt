package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni

interface JniDelegate {
    fun installSignalHandlers(
        markerFilePath: String?,
        reportId: String?,
        devLogging: Boolean,
    )
    fun onSessionChange(sessionId: String, reportPath: String)
    fun getCrashReport(path: String): String?
    fun checkForOverwrittenHandlers(): String?
    fun reinstallSignalHandlers(): Boolean
}
