package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulatorImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace

fun createSessionOrchestrator(
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
): SessionOrchestrator {
    val payloadMessageCollator = PayloadMessageCollatorImpl(
        EmbTrace.trace("sessionEnvelopeSource") { payloadSourceModule.sessionEnvelopeSource },
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
        essentialServiceModule.sessionPropertiesService
    )

    val sessionSpanAttrPopulator = SessionSpanAttrPopulatorImpl(
        essentialServiceModule.telemetryDestination,
        startupDurationProvider,
        logModule.logLimitingService,
        payloadSourceModule.metadataService
    )

    return SessionOrchestratorImpl(
        essentialServiceModule.appStateTracker,
        EmbTrace.trace("payloadFactory") { payloadFactory },
        initModule.clock,
        configService,
        essentialServiceModule.sessionTracker,
        boundaryDelegate,
        deliveryModule?.payloadStore,
        deliveryModule?.payloadCachingService,
        instrumentationModule.instrumentationRegistry,
        essentialServiceModule.telemetryDestination,
        sessionSpanAttrPopulator
    )
}
