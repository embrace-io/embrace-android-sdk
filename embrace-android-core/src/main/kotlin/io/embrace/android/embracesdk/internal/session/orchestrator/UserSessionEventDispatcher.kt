package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.logging.guardUserCode
import io.embrace.android.embracesdk.internal.session.UserSessionListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Delivers user session events to listeners. Events raised mid-transition are queued and delivered once the transition
 * completes, so a listener that calls back into the orchestrator (e.g. to end the user session) starts a fresh transition
 * instead of re-entering the in-progress one.
 */
internal class UserSessionEventDispatcher(
    private val errorHandler: ErrorHandler,
) {
    private val listeners = CopyOnWriteArrayList<UserSessionListener>()
    private val pendingEvents = ArrayDeque<SessionStateEvent>()
    private var delivering = false

    /**
     * Returns false if [listener] was already registered.
     */
    fun addListener(listener: UserSessionListener): Boolean = listeners.addIfAbsent(listener)

    fun removeListener(listener: UserSessionListener) {
        listeners.remove(listener)
    }

    fun notifyListener(listener: UserSessionListener, event: SessionStateEvent) {
        errorHandler.guardSdkCode(SdkErrorType.UserSessionCallbackFail) {
            errorHandler.guardUserCode { listener.onSessionStateEvent(event) }
        }
    }

    fun enqueue(event: SessionStateEvent) {
        pendingEvents.addLast(event)
    }

    fun deliverPending() {
        if (delivering) {
            return
        }
        delivering = true
        try {
            while (pendingEvents.isNotEmpty()) {
                val event = pendingEvents.removeFirst()
                errorHandler.guardSdkCodeEach(SdkErrorType.UserSessionCallbackFail, listeners) { listener ->
                    errorHandler.guardUserCode { listener.onSessionStateEvent(event) }
                }
            }
        } finally {
            delivering = false
        }
    }
}
