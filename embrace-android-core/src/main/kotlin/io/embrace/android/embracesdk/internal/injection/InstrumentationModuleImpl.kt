package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.InstrumentationArgsImpl
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.storage.StorageService

class InstrumentationModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    configService: ConfigService,
    essentialServiceModule: EssentialServiceModule,
    coreModule: CoreModule,
    storageService: StorageService,
    userSessionIdProvider: () -> String?,
) : InstrumentationModule {

    override val instrumentationRegistry: InstrumentationRegistry by singleton {
        InstrumentationRegistryImpl(
            initModule.logger
        )
    }

    override val instrumentationArgs: InstrumentationArgs by singleton {
        InstrumentationArgsImpl(
            configService = configService,
            logger = initModule.logger,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            destination = essentialServiceModule.telemetryDestination,
            workerThreadModule = workerThreadModule,
            store = coreModule.store,
            serializer = initModule.jsonSerializer,
            sessionPartTracker = essentialServiceModule.sessionPartTracker,
            ordinalStore = coreModule.ordinalStore,
            userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService,
            userSessionIdProvider = userSessionIdProvider,
            processIdentifier = openTelemetryModule.otelSdkConfig.processIdentifier,
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            appStateTracker = essentialServiceModule.appStateTracker,
            navigationTrackingService = essentialServiceModule.navigationTrackingService,
            telemetryService = initModule.telemetryService,
        )
    }
}
