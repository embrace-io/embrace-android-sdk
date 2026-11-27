package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.opentelemetry.kotlin.OpenTelemetry

/**
 * Methods that enable integration with the the large OTel ecosystem through standard OTel APIs and concepts. OTel
 * exporters, processors and resource attributes are configured through `Embrace.start()`.
 */
@InternalApi
public interface OTelApi {

    /**
     * Returns an [OpenTelemetry] instance that uses the API from opentelemetry-kotlin. This API
     * is currently experimental and is subject to breaking change without warning.
     */
    public fun getOpenTelemetryKotlin(): OpenTelemetry
}
