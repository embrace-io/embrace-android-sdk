package io.embrace.android.embracesdk.internal.network.http

internal object HttpUrlConnectionTracker {
    @JvmStatic
    fun registerFactory(requestContentLengthCaptureEnabled: Boolean) {
        StreamHandlerFactoryInstaller.registerFactory(requestContentLengthCaptureEnabled)
    }
}
