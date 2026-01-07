package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import okhttp3.OkHttpClient

/**
 * A module of components and services required at SDK instantiation time, i.e. before the SDK has fully started
 */
interface InitModule {

    /**
     * Clock instance locked to the time of creation used by the SDK throughout its lifetime
     */
    val clock: Clock

    /**
     * Service to track usage of public APIs and other internal metrics
     */
    val telemetryService: TelemetryService

    /**
     * Logger used by the SDK
     */
    val logger: InternalLogger

    /**
     * Info about the system available at startup time without expensive disk or API calls
     */
    val systemInfo: SystemInfo

    /**
     * Returns the serializer used to serialize data to JSON
     */
    val jsonSerializer: PlatformSerializer

    val instrumentedConfig: InstrumentedConfig

    val okHttpClient: OkHttpClient
}
