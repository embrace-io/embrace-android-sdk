package io.embrace.android.embracesdk.internal.ndk.jni

interface JniDelegate {
    fun installSignalHandlers(
        markerFilePath: String?,
        appState: String?,
        reportId: String?,
        apiLevel: Int,
        is32bit: Boolean,
        devLogging: Boolean,
    )
    fun onSessionChange(sessionId: String, reportPath: String)
    fun getCrashReport(path: String): String?
    fun checkForOverwrittenHandlers(): String?
    fun reinstallSignalHandlers(): Boolean
}
