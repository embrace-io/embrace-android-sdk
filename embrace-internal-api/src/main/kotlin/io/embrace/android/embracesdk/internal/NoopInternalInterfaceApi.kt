package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopFlutterInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopUnityInternalInterface

object NoopInternalInterfaceApi : InternalInterfaceApi {
    override val internalInterface: EmbraceInternalInterface = NoopEmbraceInternalInterface
    override val reactNativeInternalInterface: ReactNativeInternalInterface =
        NoopReactNativeInternalInterface(NoopEmbraceInternalInterface)
    override val unityInternalInterface: UnityInternalInterface =
        NoopUnityInternalInterface(NoopEmbraceInternalInterface)
    override val flutterInternalInterface: FlutterInternalInterface =
        NoopFlutterInternalInterface(NoopEmbraceInternalInterface)
}
