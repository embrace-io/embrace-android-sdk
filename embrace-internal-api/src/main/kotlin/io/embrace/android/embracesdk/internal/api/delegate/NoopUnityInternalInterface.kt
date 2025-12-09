package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface

internal class NoopUnityInternalInterface(
    private val delegate: EmbraceInternalInterface,
) : UnityInternalInterface, EmbraceInternalInterface by delegate {

    override fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?) {
    }

    override fun logUnhandledUnityException(name: String, message: String, stacktrace: String?) {
    }

    override fun logHandledUnityException(name: String, message: String, stacktrace: String?) {
    }
}
