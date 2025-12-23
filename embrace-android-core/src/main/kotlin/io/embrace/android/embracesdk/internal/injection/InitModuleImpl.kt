package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

class InitModuleImpl(
    override val logger: EmbLogger = EmbLoggerImpl(),
    override val clock: Clock = NormalizedIntervalClock(),
    override val systemInfo: SystemInfo = SystemInfo(),
) : InitModule {

    override val telemetryService: TelemetryService by singleton {
        EmbraceTelemetryService(
            systemInfo = systemInfo
        )
    }

    override val jsonSerializer: PlatformSerializer by singleton {
        EmbraceSerializer()
    }

    override val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl

    override val okHttpClient by singleton {
        EmbTrace.trace("okhttp-client-init") {
            OkHttpClient()
                .newBuilder()
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .connectTimeout(DEFAULT_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }

    companion object {
        private const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
    }
}
