package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopFlutterInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopInternalTracingApi
import io.embrace.android.embracesdk.internal.api.delegate.NoopReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopUnityInternalInterface

/**
 * Provides access to internal Embrace SDK APIs. This is intended for use by Embrace's SDKs only and is subject
 * to breaking changes without warning.
 */
class EmbraceInternalApi private constructor() : InternalInterfaceApi {

    companion object {
        const val CUSTOM_TRACE_ID_HEADER_NAME: String = "x-emb-trace-id"
        var internalTracingApi: InternalTracingApi? = null
        var internalInterfaceApi: InternalInterfaceApi? = null
        var isStarted: () -> Boolean = { false }

        private val instance = EmbraceInternalApi()

        @JvmStatic
        fun getInstance(): EmbraceInternalApi = instance
    }

    private val noopEmbraceInternalInterface by lazy {
        NoopEmbraceInternalInterface(internalTracingApi ?: NoopInternalTracingApi())
    }
    private val noopFlutterInternalInterface by lazy { NoopFlutterInternalInterface(noopEmbraceInternalInterface) }
    private val noopReactNativeInternalInterface by lazy { NoopReactNativeInternalInterface(noopEmbraceInternalInterface) }
    private val noopUnityInternalInterface by lazy { NoopUnityInternalInterface(noopEmbraceInternalInterface) }

    override val internalInterface: EmbraceInternalInterface
        get() = resolveInternalInterface(noopEmbraceInternalInterface) { internalInterfaceApi?.internalInterface }

    override val reactNativeInternalInterface: ReactNativeInternalInterface
        get() = resolveInternalInterface(noopReactNativeInternalInterface) {
            internalInterfaceApi?.reactNativeInternalInterface
        }

    override val unityInternalInterface: UnityInternalInterface
        get() = resolveInternalInterface(noopUnityInternalInterface) { internalInterfaceApi?.unityInternalInterface }

    override val flutterInternalInterface: FlutterInternalInterface
        get() = resolveInternalInterface(noopFlutterInternalInterface) { internalInterfaceApi?.flutterInternalInterface }

    private inline fun <reified T> resolveInternalInterface(
        defaultValue: T,
        provider: () -> T?,
    ): T {
        val internalInterface = runCatching(provider).getOrNull()
        return if (isStarted()) {
            internalInterface ?: defaultValue
        } else {
            defaultValue
        }
    }
}
