package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
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
    val telemetryService: InternalTelemetryService

    /**
     * Guards SDK code & buffers SDK errors until the SDK can send them
     */
    val errorHandler: ErrorHandler

    /**
     * Info about the system available at startup time without expensive disk or API calls
     */
    val systemInfo: SystemInfo

    /**
     * Returns the serializer used to serialize data to JSON
     */
    val jsonSerializer: PlatformSerializer

    val instrumentedConfig: InstrumentedConfig

    val okHttpClient: Lazy<OkHttpClient>

    val uuidSource: UuidSource

    val startupClassifier: StartupClassifier
}
