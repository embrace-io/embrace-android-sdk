package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulatorImpl

internal class SessionOrchestrationModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    dataSourceModule: DataSourceModule,
    payloadSourceModule: PayloadSourceModule,
    startupDurationProvider: (coldStart: Boolean) -> Long?,
    momentsModule: MomentsModule,
    logModule: LogModule
) : SessionOrchestrationModule {

    override val gatingService: GatingService by singleton {
        EmbraceGatingService(
            configModule.configService,
            logModule.logService,
            initModule.logger
        )
    }

    override val memoryCleanerService: MemoryCleanerService by singleton {
        EmbraceMemoryCleanerService(logger = initModule.logger)
    }

    override val payloadMessageCollator: PayloadMessageCollatorImpl by singleton {
        PayloadMessageCollatorImpl(
            Systrace.traceSynchronous("gatingService") { gatingService },
            Systrace.traceSynchronous("sessionEnvelopeSource") { payloadSourceModule.sessionEnvelopeSource },
            androidServicesModule.preferencesService,
            openTelemetryModule.currentSessionSpan,
        )
    }

    override val payloadFactory: PayloadFactory by singleton {
        PayloadFactoryImpl(
            Systrace.traceSynchronous("payloadMessageCollator") { payloadMessageCollator },
            Systrace.traceSynchronous("configService") { configModule.configService },
            initModule.logger
        )
    }

    private val boundaryDelegate by singleton {
        OrchestratorBoundaryDelegate(
            memoryCleanerService,
            essentialServiceModule.userService,
            essentialServiceModule.sessionPropertiesService
        )
    }

    override val sessionSpanAttrPopulator: SessionSpanAttrPopulator by singleton {
        SessionSpanAttrPopulatorImpl(
            openTelemetryModule.currentSessionSpan,
            momentsModule.eventService,
            startupDurationProvider,
            logModule.logService,
            payloadSourceModule.metadataService
        )
    }

    override val sessionOrchestrator: SessionOrchestrator by singleton(LoadType.EAGER) {
        SessionOrchestratorImpl(
            essentialServiceModule.processStateService,
            Systrace.traceSynchronous("payloadFactory") { payloadFactory },
            initModule.clock,
            configModule.configService,
            essentialServiceModule.sessionIdTracker,
            boundaryDelegate,
            deliveryModule.payloadStore,
            deliveryModule.payloadCachingService,
            dataSourceModule.dataCaptureOrchestrator,
            openTelemetryModule.currentSessionSpan,
            sessionSpanAttrPopulator,
            initModule.logger
        )
    }
}
