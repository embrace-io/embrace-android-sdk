package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.logging.BufferedInternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import okhttp3.OkHttpClient

class InitModuleImpl(
    preStartServices: PreStartServices = PreStartServices(),
    override val uuidSource: UuidSource = UuidSourceImpl(),
    override val startupClassifier: StartupClassifier = StartupClassifierImpl(),
    override val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl,
    override val jsonSerializer: PlatformSerializer = EmbraceSerializer(),
    override val okHttpClient: Lazy<OkHttpClient> = lazyOkHttpClient(),
) : InitModule {

    override val logger: InternalLogger = preStartServices.logger
    override val clock: Clock = preStartServices.clock
    override val systemInfo: SystemInfo = preStartServices.systemInfo
    override val telemetryService: InternalTelemetryService = preStartServices.telemetryService
    override val internalErrorHandler: BufferedInternalErrorHandler = preStartServices.internalErrorHandler
}
