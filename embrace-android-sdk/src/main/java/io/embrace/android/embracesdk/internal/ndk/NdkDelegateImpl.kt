package io.embrace.android.embracesdk.internal.ndk

@Suppress("UnusedPrivateClass")
internal class NdkDelegateImpl : NdkServiceDelegate.NdkDelegate {
    external override fun _installSignalHandlers(
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

    external override fun _updateMetaData(new_device_meta_data: String?)
    external override fun _updateSessionId(new_session_id: String?)
    external override fun _updateAppState(new_app_state: String?)
    external override fun _uninstallSignals()
    external override fun _testNativeCrash_C()
    external override fun _testNativeCrash_CPP()
    external override fun _getCrashReport(path: String?): String?
    external override fun _getErrors(path: String?): String?
    external override fun _checkForOverwrittenHandlers(): String?
    external override fun _reinstallSignalHandlers(): Boolean
}
