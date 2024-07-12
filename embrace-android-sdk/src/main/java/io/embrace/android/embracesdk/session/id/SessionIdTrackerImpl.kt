package io.embrace.android.embracesdk.session.id

import android.app.ActivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.ndk.NdkService

internal class SessionIdTrackerImpl(
    private val activityManager: ActivityManager?,
    private val logger: EmbLogger
) : SessionIdTracker {

    @Volatile
    private var sessionId: String? = null
        set(value) {
            field = value
            ndkService?.updateSessionId(value ?: "")
        }

    override var ndkService: NdkService? = null
        set(value) {
            field = value
            ndkService?.updateSessionId(sessionId ?: "")
        }

    override fun getActiveSessionId(): String? = sessionId

    override fun setActiveSessionId(sessionId: String?, isSession: Boolean) {
        this.sessionId = sessionId

        if (isSession) {
            setSessionIdToProcessStateSummary(this.sessionId)
        }
    }

    /**
     * On android 11+, we use ActivityManager#setProcessStateSummary to store sessionId
     * Then, this information will be included in the record of ApplicationExitInfo on the death of the current calling process
     *
     * @param sessionId current session id
     */
    private fun setSessionIdToProcessStateSummary(sessionId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (sessionId != null) {
                try {
                    activityManager?.setProcessStateSummary(sessionId.toByteArray())
                } catch (e: Throwable) {
                    logger.logError("Couldn't set Process State Summary", e)
                    logger.trackInternalError(InternalErrorType.PROCESS_STATE_SUMMARY_FAIL, e)
                }
            }
        }
    }
}
