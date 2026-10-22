package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.api.delegate.ForwardingInternalInterface

/**
 * Provides access to internal Embrace SDK APIs. This is intended for use by Embrace's SDKs only and is subject
 * to breaking changes without warning.
 */
object EmbraceInternalApi : InternalInterfaceApi {

    /**
     * The [InternalInterfaceApi] that calls are delegated to. Its internal interfaces are no-ops unless the SDK
     * is started.
     */
    @Volatile
    var internalInterfaceApi: InternalInterfaceApi = NoopInternalInterfaceApi

    @JvmStatic
    @Deprecated("", replaceWith = ReplaceWith("EmbraceInternalApi"))
    fun getInstance(): EmbraceInternalApi = this

    private val forwarder = ForwardingInternalInterface { internalInterfaceApi }

    override val internalInterface: EmbraceInternalInterface = forwarder

    override val reactNativeInternalInterface: ReactNativeInternalInterface = forwarder

    override val unityInternalInterface: UnityInternalInterface = forwarder

    override val flutterInternalInterface: FlutterInternalInterface = forwarder
}
