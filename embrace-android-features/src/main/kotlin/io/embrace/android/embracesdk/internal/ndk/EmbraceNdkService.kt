package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

internal class EmbraceNdkService(
    private val metadataService: MetadataService,
    private val processStateService: ProcessStateService,
    private val userService: UserService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val sharedObjectLoader: SharedObjectLoader,
    private val delegate: JniDelegate,
    private val backgroundWorker: BackgroundWorker,
    private val serializer: PlatformSerializer,
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
        backgroundWorker.submit {
            updateDeviceMetaData()
        }
    }

    override fun onUserInfoUpdate() {
        backgroundWorker.submit {
            updateDeviceMetaData()
        }
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

    /**
     * Compute NDK metadata
     */
    private fun updateDeviceMetaData() {
        if (sharedObjectLoader.loaded.get()) {
            val src = captureMetaData(true)
            var json = serializeMetadata(src)
            if (json.length >= EMB_DEVICE_META_DATA_SIZE) {
                json = serializeMetadata(src.copy(sessionProperties = null))
            }
            delegate.updateMetaData(json)
        }
    }

    private fun captureMetaData(includeSessionProperties: Boolean): NativeCrashMetadata {
        return Systrace.trace("gather-native-metadata") {
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                if (includeSessionProperties) sessionPropertiesService.getProperties() else null
            )
        }
    }

    private fun serializeMetadata(newDeviceMetaData: NativeCrashMetadata): String {
        return Systrace.trace("serialize-native-metadata") {
            serializer.toJson(newDeviceMetaData)
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
        private const val EMB_DEVICE_META_DATA_SIZE = 2048
    }
}
