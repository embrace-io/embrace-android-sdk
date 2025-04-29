package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.EmbTrace
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
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
    startupService: StartupService,
    logModule: LogModule,
) : SessionOrchestrationModule {

    override val gatingService: GatingService by singleton {
        EmbraceGatingService(
            configModule.configService,
            logModule.logService
        )
    }

    override val memoryCleanerService: MemoryCleanerService by singleton {
        EmbraceMemoryCleanerService(logger = initModule.logger)
    }

    override val payloadMessageCollator: PayloadMessageCollatorImpl by singleton {
        PayloadMessageCollatorImpl(
            EmbTrace.trace("gatingService") { gatingService },
            EmbTrace.trace("sessionEnvelopeSource") { payloadSourceModule.sessionEnvelopeSource },
            androidServicesModule.preferencesService,
            openTelemetryModule.currentSessionSpan,
        )
    }

    override val payloadFactory: PayloadFactory by singleton {
        PayloadFactoryImpl(
            EmbTrace.trace("payloadMessageCollator") { payloadMessageCollator },
            EmbTrace.trace("logEnvelopeSource") { payloadSourceModule.logEnvelopeSource },
            EmbTrace.trace("configService") { configModule.configService },
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
            startupService,
            logModule.logService,
            payloadSourceModule.metadataService
        )
    }

    override val sessionOrchestrator: SessionOrchestrator by singleton(LoadType.EAGER) {
        SessionOrchestratorImpl(
            essentialServiceModule.processStateService,
            EmbTrace.trace("payloadFactory") { payloadFactory },
            initModule.clock,
            configModule.configService,
            essentialServiceModule.sessionIdTracker,
            boundaryDelegate,
            deliveryModule.payloadStore,
            deliveryModule.payloadCachingService,
            dataSourceModule.dataCaptureOrchestrator,
            openTelemetryModule.currentSessionSpan,
            sessionSpanAttrPopulator
        )
    }
}
