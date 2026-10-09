package io.embrace.android.embracesdk.internal

/**
 * Provides access to internal Embrace SDK APIs. This is intended for use by Embrace's SDKs only and is subject
 * to breaking changes without warning.
 */
object EmbraceInternalApi : InternalInterfaceApi {

    /**
     * The [InternalInterfaceApi] that calls are delegated to. This is [NoopInternalInterfaceApi] unless the SDK
     * is started.
     */
    @Volatile
    var internalInterfaceApi: InternalInterfaceApi = NoopInternalInterfaceApi

    @JvmStatic
    @Deprecated("", replaceWith = ReplaceWith("EmbraceInternalApi"))
    fun getInstance(): EmbraceInternalApi = this

    override val internalInterface: EmbraceInternalInterface
        get() = internalInterfaceApi.internalInterface

    override val reactNativeInternalInterface: ReactNativeInternalInterface
        get() = internalInterfaceApi.reactNativeInternalInterface

    override val unityInternalInterface: UnityInternalInterface
        get() = internalInterfaceApi.unityInternalInterface

    override val flutterInternalInterface: FlutterInternalInterface
        get() = internalInterfaceApi.flutterInternalInterface
}
