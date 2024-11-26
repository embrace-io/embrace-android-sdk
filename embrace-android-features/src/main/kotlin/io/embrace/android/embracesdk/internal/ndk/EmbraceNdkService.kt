package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

internal class EmbraceNdkService(
    private val processStateService: ProcessStateService,
    private val userService: UserService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val sharedObjectLoader: SharedObjectLoader,
    private val delegate: JniDelegate,
) : NdkService, ProcessStateListener {

    override fun initializeService(sessionIdTracker: SessionIdTracker) {
        Systrace.traceSynchronous("init-ndk-service") {
            processStateService.addListener(this)
            userService.addUserInfoListener(::onUserInfoUpdate)
            sessionIdTracker.addListener { updateSessionId(it ?: "") }
            sessionPropertiesService.addChangeListener(::onSessionPropertiesUpdate)
        }
    }

    override fun updateSessionId(newSessionId: String) {
        if (sharedObjectLoader.loaded.get()) {
            delegate.updateSessionId(newSessionId)
        }
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        // do nothing
    }

    override fun onUserInfoUpdate() {
        // do nothing
    }

    override fun onBackground(timestamp: Long) {
        updateAppState(APPLICATION_STATE_BACKGROUND)
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        updateAppState(APPLICATION_STATE_FOREGROUND)
    }

    private fun updateAppState(newAppState: String) {
        if (sharedObjectLoader.loaded.get()) {
            delegate.updateAppState(newAppState)
        }
    }

    internal companion object {
        /**
         * Signals to the API that the application was in the foreground.
         */
        private const val APPLICATION_STATE_FOREGROUND = "foreground"

        /**
         * Signals to the API that the application was in the background.
         */
        private const val APPLICATION_STATE_BACKGROUND = "background"
    }
}
