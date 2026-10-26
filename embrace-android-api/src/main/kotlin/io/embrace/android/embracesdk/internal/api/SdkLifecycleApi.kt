package io.embrace.android.embracesdk.internal.api

import android.content.Context
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Declares the functions of Embrace's public API that start and disable the SDK. You should not use
 * [SdkLifecycleApi] directly or implement it in your own custom classes, as new functions may be
 * added in the future. Use the Embrace class instead.
 */
@InternalApi
public interface SdkLifecycleApi {

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     *
     * See [Embrace Docs](https://embrace.io/docs/android/) for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context an instance of the application context
     */
    public fun start(context: Context)

    /**
     * If a user wishes to opt-out of exporting data to Embrace, you should:
     *
     * (1) Persist this user preference somewhere that can be readily accessed between processes
     * (2) On the next process launch, read this preference & only initialize the Embrace SDK if you wish to capture data
     * (3) Call this function if the SDK has already been initialized.
     *
     * When the SDK has already been initialized this function will prevent the SDK from exporting any further data
     * via HTTP requests or OTel exports, and will delete any persisted data that has not yet been exported.
     *
     * The SDK makes a best effort attempt. Some data capture/handlers may remain active until the next process launch
     * due to technical reasons, but any captured data will not be exported.
     */
    public fun disable()
}
