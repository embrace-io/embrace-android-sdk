package io.embrace.android.embracesdk.internal.ndk.jni

interface JniDelegate {
    fun installSignalHandlers(
        reportPath: String?,
        markerFilePath: String?,
        sessionId: String?,
        appState: String?,
        reportId: String?,
        apiLevel: Int,
        is32bit: Boolean,
        devLogging: Boolean,
    )
    fun updateMetaData(metadata: String?)
    fun updateSessionId(sessionId: String?)
    fun updateAppState(appState: String?)
    fun getCrashReport(path: String?): String?
    fun checkForOverwrittenHandlers(): String?
    fun reinstallSignalHandlers(): Boolean
}
