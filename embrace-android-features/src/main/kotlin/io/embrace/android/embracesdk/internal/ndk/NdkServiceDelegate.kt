package io.embrace.android.embracesdk.internal.ndk

public class NdkServiceDelegate {
    public interface NdkDelegate {
        public fun _installSignalHandlers(
            report_path: String?,
            markerFilePath: String?,
            device_meta_data: String?,
            session_id: String?,
            app_state: String?,
            report_id: String?,
            api_level: Int,
            is_32bit: Boolean,
            dev_logging: Boolean
        )

        public fun _updateMetaData(new_device_meta_data: String?)
        public fun _updateSessionId(new_session_id: String?)
        public fun _updateAppState(new_app_state: String?)
        public fun _uninstallSignals()
        public fun _testNativeCrash_C()
        public fun _testNativeCrash_CPP()
        public fun _getCrashReport(path: String?): String?
        public fun _getErrors(path: String?): String?
        public fun _checkForOverwrittenHandlers(): String?
        public fun _reinstallSignalHandlers(): Boolean
    }
}
