package io.embrace.android.embracesdk.internal.ndk.jni

class JniDelegateImpl : JniDelegate {
    external override fun installSignalHandlers(
        reportPath: String,
        markerFilePath: String?,
        sessionId: String?,
        appState: String?,
        reportId: String?,
        apiLevel: Int,
        is32bit: Boolean,
        devLogging: Boolean,
    )

    external override fun updateMetaData(metadata: String?)
    external override fun onSessionChange(sessionId: String?)
    external override fun updateAppState(appState: String?)
    external override fun getCrashReport(path: String?): String?
    external override fun checkForOverwrittenHandlers(): String?
    external override fun reinstallSignalHandlers(): Boolean
}
