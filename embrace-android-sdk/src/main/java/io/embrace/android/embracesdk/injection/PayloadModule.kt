package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.ndk.NativeModule

/**
 * Modules containing classes that generate the payloads.
 */
internal interface PayloadModule {
    val sessionEnvelopeSource: SessionEnvelopeSource
    val logEnvelopeSource: LogEnvelopeSource
}

internal class PayloadModuleImpl(
    private val essentialServiceModule: EssentialServiceModule,
    private val nativeModule: NativeModule,
    private val otelModule: OpenTelemetryModule,
    private val sdkObservabilityModule: SdkObservabilityModule
) : PayloadModule {

    private val metadataSource by singleton {
        EnvelopeMetadataSourceImpl(essentialServiceModule.userService)
    }

    private val resourceSource by singleton {
        EnvelopeResourceSourceImpl()
    }

    private val sessionPayloadSource by singleton {
        SessionPayloadSourceImpl(
            sdkObservabilityModule.internalErrorService,
            nativeModule.nativeThreadSamplerService,
            otelModule.spanSink,
            otelModule.currentSessionSpan,
            otelModule.spanRepository
        )
    }

    private val logPayloadSource by singleton {
        LogPayloadSourceImpl(
            otelModule.logSink
        )
    }

    override val sessionEnvelopeSource: SessionEnvelopeSource by singleton {
        SessionEnvelopeSourceImpl(metadataSource, resourceSource, sessionPayloadSource)
    }

    override val logEnvelopeSource: LogEnvelopeSource by singleton {
        LogEnvelopeSourceImpl(metadataSource, resourceSource, logPayloadSource)
    }
}
