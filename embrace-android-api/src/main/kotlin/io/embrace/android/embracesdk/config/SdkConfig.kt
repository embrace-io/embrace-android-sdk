package io.embrace.android.embracesdk.config

/**
 * Configures the SDK when it starts. Pass a block that receives this to `Embrace.start()`:
 *
 * ```
 * Embrace.start(context) {
 *     otel {
 *         addSpanExporter(exporter)
 *     }
 * }
 * ```
 *
 * The configuration only applies during start, so calls made on a reference that outlives the block are ignored. An
 * exception thrown by the block is logged rather than propagated, and the SDK still starts. Each call made before the
 * exception still applies.
 */
@SdkConfigDsl
public interface SdkConfig {

    /**
     * Configures how the SDK integrates with OpenTelemetry.
     */
    public fun otel(action: OTelConfig.() -> Unit)

    /**
     * Configures how the app observes user sessions.
     */
    public fun userSession(action: UserSessionConfig.() -> Unit)
}
