package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.api.delegate.NoopHostedSdkInternalInterface

object NoopInternalInterfaceApi : InternalInterfaceApi {
    override val internalInterface: EmbraceInternalInterface = NoopHostedSdkInternalInterface
    override val reactNativeInternalInterface: ReactNativeInternalInterface = NoopHostedSdkInternalInterface
    override val unityInternalInterface: UnityInternalInterface = NoopHostedSdkInternalInterface
    override val flutterInternalInterface: FlutterInternalInterface = NoopHostedSdkInternalInterface
}
