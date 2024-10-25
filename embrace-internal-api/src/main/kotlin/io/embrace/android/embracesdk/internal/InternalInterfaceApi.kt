package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @suppress
 */
@InternalApi
interface InternalInterfaceApi {

    /**
     * Get internal interface for the intra-Embrace, not-publicly-supported API
     * @suppress
     */
    @InternalApi
    val internalInterface: EmbraceInternalInterface

    /**
     * Gets the [ReactNativeInternalInterface] that should be used as the sole source of
     * communication with the Android SDK for React Native. Not part of the supported public API.
     * @suppress
     */
    @InternalApi
    val reactNativeInternalInterface: ReactNativeInternalInterface

    /**
     * Gets the [UnityInternalInterface] that should be used as the sole source of
     * communication with the Android SDK for Unity. Not part of the supported public API.
     * @suppress
     */
    @InternalApi
    val unityInternalInterface: UnityInternalInterface

    /**
     * Gets the [FlutterInternalInterface] that should be used as the sole source of
     * communication with the Android SDK for Flutter. Not part of the supported public API.
     * @suppress
     */
    @InternalApi
    val flutterInternalInterface: FlutterInternalInterface
}
