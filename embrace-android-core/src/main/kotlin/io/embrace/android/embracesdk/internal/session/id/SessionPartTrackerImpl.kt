package io.embrace.android.embracesdk.internal.session.id

import android.app.ActivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import java.util.concurrent.CopyOnWriteArraySet

internal class SessionPartTrackerImpl(
    private val activityManager: Lazy<ActivityManager?>,
    private val errorHandler: ErrorHandler,
) : SessionPartTracker {

    private val sessionChangeListeners = CopyOnWriteArraySet<SessionPartChangeListener>()
    private val sessionEndListeners = CopyOnWriteArraySet<SessionPartEndListener>()

    @Volatile
    private var activeSession: SessionPartToken? = null

    override fun addSessionPartChangeListener(listener: SessionPartChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun addSessionPartEndListener(listener: SessionPartEndListener) {
        sessionEndListeners.add(listener)
    }

    override fun getActiveSessionPart(): SessionPartToken? = activeSession

    override fun newActiveSessionPart(
        endSessionPartCallback: SessionPartToken.() -> Unit,
        startSessionPartCallback: () -> SessionPartToken?,
        postTransitionProcessState: ProcessState,
    ): SessionPartToken? {
        activeSession?.let { endingSession ->
            errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, sessionEndListeners) {
                it.onPreSessionEnd()
            }
            endingSession.endSessionPartCallback()
        }

        activeSession = startSessionPartCallback()
        errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, sessionChangeListeners) {
            it.onPostSessionChange()
        }

        return activeSession
    }

    override fun setProcessStateSummary(sessionPartId: String, userSessionId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            errorHandler.guardSdkCode(SdkErrorType.ProcessStateSummaryFail) {
                activityManager.value?.setProcessStateSummary("${sessionPartId}_$userSessionId".toByteArray())
            }
        }
    }
}
