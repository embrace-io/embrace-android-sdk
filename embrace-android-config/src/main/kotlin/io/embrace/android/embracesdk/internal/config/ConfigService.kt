package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.TraceparentInjectionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.UserSessionBehavior
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Provides access to the configuration for the customer's app.
 *
 * Configuration is configured for the user's app, and exposed via the API.
 */
interface ConfigService {

    /**
     * The resolved config.
     */
    val config: EmbraceConfig

    /**
     * How sessions should behave.
     */
    val sessionBehavior: UserSessionBehavior

    /**
     * How network call capture should behave.
     */
    val networkBehavior: NetworkBehavior

    /**
     * How the SDK should handle events where data can be captured. This could be a moment, etc...
     */
    val dataCaptureEventBehavior: DataCaptureEventBehavior

    /**
     * How the traceparent injection feature should behave
     */
    val traceparentInjectionBehavior: TraceparentInjectionBehavior

    /**
     * How the network span forwarding feature should behave
     */
    val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior

    /**
     * Provides behavior for keys that might be sensitive and should be redacted when they are sent to the server
     */
    val sensitiveKeysBehavior: SensitiveKeysBehavior

    /**
     * Provides behavior for OpenTelemetry configuration
     */
    val otelBehavior: OtelBehavior

    /**
     * Codes of pct flags that are mid-rollout and enabled for this device.
     */
    val enabledPctRollouts: List<String>

    /**
     * The app framework that is currently in use.
     */
    val appFramework: AppFramework

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    val appId: String?

    /**
     * Whether only OTel exporters should be used. If this returns true,
     * the SDK should avoid enabling unnecessary systems (such as anything that creates requests
     * to Embrace).
     */
    fun isOnlyUsingOtelExporters(): Boolean

    /**
     * Provides build-related metadata for the app.
     */
    val buildInfo: BuildInfo

    /**
     * Unique identifier for this device that is persisted per-install.
     */
    val deviceId: String

    /**
     * The current native symbols.
     */
    val nativeSymbolMap: Map<String, String>?
}
