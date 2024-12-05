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
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Provides access to the configuration for the customer's app.
 *
 * Configuration is configured for the user's app, and exposed via the API.
 */
interface ConfigService {

    /**
     * How background activity functionality should behave.
     */
    val backgroundActivityBehavior: BackgroundActivityBehavior

    /**
     * How automatic data capture functionality should behave.
     */
    val autoDataCaptureBehavior: AutoDataCaptureBehavior

    /**
     * How automatic breadcrumb functionality should behave.
     */
    val breadcrumbBehavior: BreadcrumbBehavior

    /**
     * How log message functionality should behave.
     */
    val logMessageBehavior: LogMessageBehavior

    /**
     * How ANR functionality should behave.
     */
    val anrBehavior: AnrBehavior

    /**
     * How sessions should behave.
     */
    val sessionBehavior: SessionBehavior

    /**
     * How network call capture should behave.
     */
    val networkBehavior: NetworkBehavior

    /**
     * How the SDK should handle events where data can be captured. This could be a moment, etc...
     */
    val dataCaptureEventBehavior: DataCaptureEventBehavior

    /**
     * Provides whether the SDK should enable certain 'behavior' modes, such as 'integration mode'
     */
    val sdkModeBehavior: SdkModeBehavior

    /**
     * Provides whether the SDK should enable certain 'behavior' of web vitals
     */
    val webViewVitalsBehavior: WebViewVitalsBehavior

    /**
     * Provides behavior for the app exit info feature
     */
    val appExitInfoBehavior: AppExitInfoBehavior

    /**
     * How the network span forwarding feature should behave
     */
    val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior

    /**
     * Provides behavior for keys that might be sensitive and should be redacted when they are sent to the server
     */
    val sensitiveKeysBehavior: SensitiveKeysBehavior

    /**
     * The app framework that is currently in use.
     */
    val appFramework: AppFramework

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    val appId: String?
}
