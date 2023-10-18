package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.config.behavior.SdkAppBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import java.io.Closeable

/**
 * Provides access to basic configuration obtained from the Local Config file. This service
 * obtains configs like URLs and App ID, which are needed by the Config Service and other services.
 * Having these configs in a separate service allows us to avoid circular dependencies.
 */
internal interface CoreConfigService : Closeable {

    /**
     * Provides the App ID and basic information about the app behavior.
     */
    val sdkAppBehavior: SdkAppBehavior

    /**
     * Provides base endpoints the SDK should send data to.
     */
    val sdkEndpointBehavior: SdkEndpointBehavior
}
