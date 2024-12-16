@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.api

import android.content.Context
import io.embrace.android.embracesdk.AppFramework
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Declares the functions that consist of Embrace's public API - specifically
 * those that are only declared on Android. You should not use
 * [EmbraceAndroidApi] directly or implement it in your own custom classes,
 * as new functions may be added in future. Use the Embrace class instead.
 */
@InternalApi
public interface EmbraceAndroidApi {

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
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     *
     * See [Embrace Docs](https://embrace.io/docs/android/) for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context                  an instance of context
     * @param appFramework             the AppFramework of the application
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use {@link #start(Context)} instead.")
    public fun start(
        context: Context,
        appFramework: AppFramework,
    )

    /**
     * Records that a view 'started'. You should call this when your app starts displaying an
     * activity, a fragment, a screen, or any custom UI element, and you want to capture a
     * breadcrumb that this happens.
     *
     * A matching call to [.endView] must be made when the view is no longer
     * displayed.
     *
     * A maximum of 100 breadcrumbs will be recorded per session, with a maximum length of 256
     * characters per view name.
     *
     * @param name the name of the view to log
     */
    public fun startView(name: String): Boolean

    /**
     * Records that a view 'ended'. You should call this when your app stops displaying an
     * activity, a fragment, a screen, or any custom UI element, and you want to capture a
     * breadcrumb that this happens.
     *
     * A matching call to [.startView] must be made when the view is first
     * displayed, or no breadcrumb will be logged.
     *
     * A maximum of 100 breadcrumbs will be recorded per session, with a maximum length of 256
     * characters per view name.
     *
     * @param name the name of the view to log
     */
    public fun endView(name: String): Boolean

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
