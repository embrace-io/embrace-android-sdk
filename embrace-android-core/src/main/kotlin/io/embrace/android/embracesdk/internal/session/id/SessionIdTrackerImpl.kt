package io.embrace.android.embracesdk.internal.session.id

import android.app.ActivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.concurrent.CopyOnWriteArraySet

public class SessionIdTrackerImpl(
    private val activityManager: ActivityManager?,
    private val logger: EmbLogger
) : SessionIdTracker {

    private val listeners = CopyOnWriteArraySet<(String?) -> Unit>()

    @Volatile
    private var sessionId: String? = null
        set(value) {
            field = value
            listeners.forEach { it(value) }
        }

    override fun addListener(listener: (String?) -> Unit) {
        listeners.add(listener)
        listener(sessionId)
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
