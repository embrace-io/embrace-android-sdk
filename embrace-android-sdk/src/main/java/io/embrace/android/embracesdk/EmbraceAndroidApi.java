package io.embrace.android.embracesdk;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Declares the functions that consist of Embrace's public API - specifically
 * those that are only declared on Android. You should not use
 * {@link EmbraceAndroidApi} directly or implement it in your own custom classes,
 * as new functions may be added in future. Use the {@link Embrace} class instead.
 */
interface EmbraceAndroidApi extends EmbraceApi {

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     * <p>
     * See <a href="https://docs.embrace.io/docs/android-integration-guide">Embrace Docs</a> for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context an instance of the application context
     */
    void start(@NonNull Context context);

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     * <p>
     * See <a href="https://docs.embrace.io/docs/android-integration-guide">Embrace Docs</a> for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context                  an instance of context
     * @param enableIntegrationTesting if true, debug sessions (those which are not part of a
     *                                 release APK) will go to the live integration testing tab
     *                                 of the dashboard. If false, they will appear in 'recent
     *                                 sessions'.
     */
    void start(@NonNull Context context,
               boolean enableIntegrationTesting);

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     * <p>
     * See <a href="https://docs.embrace.io/docs/android-integration-guide">Embrace Docs</a> for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context                  an instance of context
     * @param enableIntegrationTesting if true, debug sessions (those which are not part of a
     *                                 release APK) will go to the live integration testing tab
     *                                 of the dashboard. If false, they will appear in 'recent
     *                                 sessions'.
     */
    void start(@NonNull Context context,
               boolean enableIntegrationTesting,
               @NonNull Embrace.AppFramework appFramework);

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    boolean isStarted();

    /**
     * Records that a view 'started'. You should call this when your app starts displaying an
     * activity, a fragment, a screen, or any custom UI element, and you want to capture a
     * breadcrumb that this happens.
     * <p>
     * A matching call to {@link #endView(String)} must be made when the view is no longer
     * displayed.
     * <p>
     * A maximum of 100 breadcrumbs will be recorded per session, with a maximum length of 256
     * characters per view name.
     *
     * @param name the name of the view to log
     */
    boolean startView(@NonNull String name);

    /**
     * Records that a view 'ended'. You should call this when your app stops displaying an
     * activity, a fragment, a screen, or any custom UI element, and you want to capture a
     * breadcrumb that this happens.
     * <p>
     * A matching call to {@link #startView(String)} must be made when the view is first
     * displayed, or no breadcrumb will be logged.
     * <p>
     * A maximum of 100 breadcrumbs will be recorded per session, with a maximum length of 256
     * characters per view name.
     *
     * @param name the name of the view to log
     */
    boolean endView(@NonNull String name);
}
