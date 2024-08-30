package io.embrace.android.embracesdk.internal.ndk

internal class NdkServiceDelegate {
    interface NdkDelegate {
        fun _installSignalHandlers(
            report_path: String?,
            markerFilePath: String?,
            session_id: String?,
            app_state: String?,
            report_id: String?,
            api_level: Int,
            is_32bit: Boolean,
            dev_logging: Boolean
        )

        fun _updateSessionId(new_session_id: String?)
        fun _updateAppState(new_app_state: String?)
        fun _testNativeCrash_C()
        fun _testNativeCrash_CPP()
        fun _getCrashReport(path: String?): String?
        fun _getErrors(path: String?): String?
        fun _checkForOverwrittenHandlers(): String?
        fun _reinstallSignalHandlers(): Boolean
    }
}
