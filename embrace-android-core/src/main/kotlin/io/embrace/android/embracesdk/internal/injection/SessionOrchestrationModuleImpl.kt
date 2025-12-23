package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulatorImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace

class SessionOrchestrationModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    deliveryModule: DeliveryModule,
    instrumentationModule: InstrumentationModule,
    payloadSourceModule: PayloadSourceModule,
    startupDurationProvider: () -> Long?,
    logModule: LogModule,
) : SessionOrchestrationModule {

    private val payloadMessageCollator: PayloadMessageCollatorImpl by singleton {
        PayloadMessageCollatorImpl(
            EmbTrace.trace("sessionEnvelopeSource") { payloadSourceModule.sessionEnvelopeSource },
            coreModule.ordinalStore,
            openTelemetryModule.currentSessionSpan,
        )
    }

    private val payloadFactory: PayloadFactory by singleton {
        PayloadFactoryImpl(
            EmbTrace.trace("payloadMessageCollator") { payloadMessageCollator },
            EmbTrace.trace("logEnvelopeSource") { payloadSourceModule.logEnvelopeSource },
            EmbTrace.trace("configService") { configService },
            initModule.logger
        )
    }

    private val boundaryDelegate by singleton {
        OrchestratorBoundaryDelegate(
            essentialServiceModule.userService,
            essentialServiceModule.sessionPropertiesService
        )
    }

    private val sessionSpanAttrPopulator: SessionSpanAttrPopulator by singleton {
        SessionSpanAttrPopulatorImpl(
            essentialServiceModule.telemetryDestination,
            startupDurationProvider,
            logModule.logService,
            payloadSourceModule.metadataService
        )
    }

    override val sessionOrchestrator: SessionOrchestrator by singleton {
        SessionOrchestratorImpl(
            essentialServiceModule.appStateTracker,
            EmbTrace.trace("payloadFactory") { payloadFactory },
            initModule.clock,
            configService,
            essentialServiceModule.sessionTracker,
            boundaryDelegate,
            deliveryModule.payloadStore,
            deliveryModule.payloadCachingService,
            instrumentationModule.instrumentationRegistry,
            essentialServiceModule.telemetryDestination,
            sessionSpanAttrPopulator
        )
    }
}
