package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.envelope.session.OtelPayloadMapperImpl
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl

internal class PayloadSourceModuleImpl(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
) : PayloadSourceModule {

    private val sessionPayloadSource by singleton {
        SessionPayloadSourceImpl(
            { nativeModule.nativeThreadSamplerService?.getNativeSymbols() },
            otelModule.spanSink,
            otelModule.currentSessionSpan,
            otelModule.spanRepository,
            OtelPayloadMapperImpl(
                anrModule.anrOtelMapper,
                nativeModule.nativeAnrOtelMapper,
            ),
            initModule.logger
        )
    }

    private val logPayloadSource by singleton {
        LogPayloadSourceImpl(
            otelModule.logSink
        )
    }

    override val sessionEnvelopeSource: SessionEnvelopeSource by singleton {
        SessionEnvelopeSourceImpl(
            essentialServiceModule.metadataSource,
            essentialServiceModule.resourceSource,
            sessionPayloadSource
        )
    }

    override val logEnvelopeSource: LogEnvelopeSource by singleton {
        LogEnvelopeSourceImpl(
            essentialServiceModule.metadataSource,
            essentialServiceModule.resourceSource,
            logPayloadSource
        )
    }
}
