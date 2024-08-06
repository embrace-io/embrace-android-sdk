package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework
import java.io.Closeable

/**
 * Provides access to the configuration for the customer's app.
 *
 * Configuration is configured for the user's app, and exposed via the API.
 */
public interface ConfigService : Closeable {

    /**
     * How background activity functionality should behave.
     */
    public val backgroundActivityBehavior: BackgroundActivityBehavior

    /**
     * How automatic data capture functionality should behave.
     */
    public val autoDataCaptureBehavior: AutoDataCaptureBehavior

    /**
     * How automatic breadcrumb functionality should behave.
     */
    public val breadcrumbBehavior: BreadcrumbBehavior

    /**
     * How log message functionality should behave.
     */
    public val logMessageBehavior: LogMessageBehavior

    /**
     * How ANR functionality should behave.
     */
    public val anrBehavior: AnrBehavior

    /**
     * How sessions should behave.
     */
    public val sessionBehavior: SessionBehavior

    /**
     * How network call capture should behave.
     */
    public val networkBehavior: NetworkBehavior

    /**
     * How the startup moment should behave
     */
    public val startupBehavior: StartupBehavior

    /**
     * How the SDK should handle events where data can be captured. This could be a moment, etc...
     */
    public val dataCaptureEventBehavior: DataCaptureEventBehavior

    /**
     * Provides whether the SDK should enable certain 'behavior' modes, such as 'integration mode'
     */
    public val sdkModeBehavior: SdkModeBehavior

    /**
     * Provides base endpoints the SDK should send data to
     */
    public val sdkEndpointBehavior: SdkEndpointBehavior

    /**
     * Provides whether the SDK should enable certain 'behavior' of web vitals
     */
    public val webViewVitalsBehavior: WebViewVitalsBehavior

    /**
     * Provides behavior for the app exit info feature
     */
    public val appExitInfoBehavior: AppExitInfoBehavior

    /**
     * How the network span forwarding feature should behave
     */
    public val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior

    /**
     * Provides behavior for keys that might be sensitive and should be redacted when they are sent to the server
     */
    public val sensitiveKeysBehavior: SensitiveKeysBehavior

    /**
     * The app framework that is currently in use.
     */
    public val appFramework: AppFramework

    /**
     * Adds a listener for changes to the [RemoteConfig]. The listeners will be notified when the
     * [ConfigService] refreshes its configuration.
     *
     * @param configListener the listener to add
     */
    public fun addListener(configListener: () -> Unit)

    /**
     * Checks if the SDK is enabled.
     *
     * The SDK can be configured to disable a percentage of devices based on the normalization of
     * their device ID between 1-100. This threshold is set in [RemoteConfig].
     *
     * @return true if the sdk is enabled, false otherwise
     */
    public fun isSdkDisabled(): Boolean

    /**
     * Checks if the capture of background activity is enabled.
     *
     *
     * The background activity capture can be configured to enable a percentage of
     * devices based on the normalization of their device ID between 1-100.
     *
     * @return true if background activity capture is enabled.
     */
    public fun isBackgroundActivityCaptureEnabled(): Boolean

    /**
     * Returns true if the remote config has been fetched and is not expired. Generally speaking
     * use of this function should be discouraged - but it can be useful to prevent running risky
     * behavior that should only be switched on via remote config.
     *
     * Most callers will not need this function - try not to abuse it.
     */
    public fun hasValidRemoteConfig(): Boolean

    /**
     * Checks if the capture of Application Exit Info is enabled.
     *
     * @return true if AEI capture is enabled.
     */
    public fun isAppExitInfoCaptureEnabled(): Boolean
}
