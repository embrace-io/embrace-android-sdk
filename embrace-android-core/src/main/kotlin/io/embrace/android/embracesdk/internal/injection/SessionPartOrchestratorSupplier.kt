package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulatorImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace

fun createSessionPartOrchestrator(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    deliveryModule: DeliveryModule?,
    instrumentationModule: InstrumentationModule,
    payloadSourceModule: PayloadSourceModule,
    startupDurationProvider: () -> Long?,
    logModule: LogModule,
): SessionPartOrchestrator {
    val payloadMessageCollator = PayloadMessageCollatorImpl(
        EmbTrace.trace("sessionEnvelopeSource") { payloadSourceModule.sessionPartEnvelopeSource },
        coreModule.ordinalStore,
        openTelemetryModule.currentSessionSpan,
    )

    val payloadFactory = PayloadFactoryImpl(
        EmbTrace.trace("payloadMessageCollator") { payloadMessageCollator },
        EmbTrace.trace("logEnvelopeSource") { payloadSourceModule.logEnvelopeSource },
        EmbTrace.trace("configService") { configService },
        initModule.logger
    )

    val boundaryDelegate = OrchestratorBoundaryDelegate(
        essentialServiceModule.userService,
        essentialServiceModule.userSessionPropertiesService
    )

    val sessionSpanAttrPopulator = SessionSpanAttrPopulatorImpl(
        essentialServiceModule.telemetryDestination,
        startupDurationProvider,
        logModule.logLimitingService,
        payloadSourceModule.metadataService
    )

    return SessionPartOrchestratorImpl(
        essentialServiceModule.appStateTracker,
        EmbTrace.trace("payloadFactory") { payloadFactory },
        initModule.clock,
        configService,
        essentialServiceModule.sessionPartTracker,
        boundaryDelegate,
        deliveryModule?.payloadStore,
        deliveryModule?.payloadCachingService,
        instrumentationModule.instrumentationRegistry,
        essentialServiceModule.telemetryDestination,
        sessionSpanAttrPopulator
    )
}
