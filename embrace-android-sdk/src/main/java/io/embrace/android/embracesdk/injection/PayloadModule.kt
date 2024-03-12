package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.envelope.LogEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.SessionEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.log.LogSourceImpl
import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSourceImpl
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
            otelModule.currentSessionSpan
        )
    }

    private val logSource by singleton {
        LogSourceImpl()
    }

    override val sessionEnvelopeSource: SessionEnvelopeSource by singleton {
        SessionEnvelopeSource(metadataSource, resourceSource, sessionPayloadSource)
    }

    override val logEnvelopeSource: LogEnvelopeSource by singleton {
        LogEnvelopeSource(metadataSource, resourceSource, logSource)
    }
}
