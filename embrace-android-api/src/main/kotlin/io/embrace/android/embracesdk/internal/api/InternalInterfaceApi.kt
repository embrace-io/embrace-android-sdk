package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.FlutterInternalInterface
import io.embrace.android.embracesdk.ReactNativeInternalInterface
import io.embrace.android.embracesdk.UnityInternalInterface
import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

public interface InternalInterfaceApi {

    /**
     * Get internal interface for the intra-Embrace, not-publicly-supported API
     */
    @InternalApi
    public val internalInterface: EmbraceInternalInterface

    /**
     * Gets the [ReactNativeInternalInterface] that should be used as the sole source of
     * communication with the Android SDK for React Native. Not part of the supported public API.
     */
    @InternalApi
    public val reactNativeInternalInterface: ReactNativeInternalInterface?

    /**
     * @hide Gets the [UnityInternalInterface] that should be used as the sole source of
     * communication with the Android SDK for Unity. Not part of the supported public API.
     * @hide
     */
    @InternalApi
    public val unityInternalInterface: UnityInternalInterface?

    /**
     * Gets the [FlutterInternalInterface] that should be used as the sole source of
     * communication with the Android SDK for Flutter. Not part of the supported public API.
     *
     * @hide
     */
    @InternalApi
    public val flutterInternalInterface: FlutterInternalInterface?
}
