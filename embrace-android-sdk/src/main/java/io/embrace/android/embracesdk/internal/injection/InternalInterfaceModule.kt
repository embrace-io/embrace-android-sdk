package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface

internal interface InternalInterfaceModule {
    val embraceInternalInterface: EmbraceInternalInterface
    val reactNativeInternalInterface: ReactNativeInternalInterface
    val unityInternalInterface: UnityInternalInterface
    val flutterInternalInterface: FlutterInternalInterface
}
