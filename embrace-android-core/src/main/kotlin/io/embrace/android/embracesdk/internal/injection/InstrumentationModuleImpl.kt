package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.InstrumentationArgsImpl
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.EmbTrace

class InstrumentationModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadPool: WorkerThreadPool,
    configService: ConfigService,
    essentialServiceModule: EssentialServiceModule,
    coreModule: CoreModule,
    storageService: StorageService,
    userSessionIdsProvider: () -> String?,
    activeSessionIdsProvider: () -> SessionIdsSnapshot,
) : InstrumentationModule {

    override val instrumentationRegistry: InstrumentationRegistry = EmbTrace.trace("instrumentation-registry-init") {
        InstrumentationRegistryImpl(
            initModule.errorHandler,
        )
    }

    override val instrumentationArgs: InstrumentationArgs = EmbTrace.trace("instrumentation-args-init") {
        InstrumentationArgsImpl(
            configService = configService,
            errorHandler = initModule.errorHandler,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            destination = essentialServiceModule.telemetryDestination,
            workerThreadPool = workerThreadPool,
            storeProvider = { coreModule.store },
            serializer = initModule.jsonSerializer,
            uuidSource = initModule.uuidSource,
            sessionPartTracker = essentialServiceModule.sessionPartTracker,
            ordinalStore = coreModule.ordinalStore,
            userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService,
            userSessionIdsProvider = userSessionIdsProvider,
            activeSessionIdsProvider = activeSessionIdsProvider,
            processIdentifier = openTelemetryModule.otelSdkConfig.processIdentifier,
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            processStateTracker = essentialServiceModule.processStateTracker,
            navigationTrackingService = essentialServiceModule.navigationTrackingService,
            telemetryService = initModule.telemetryService,
            eventBus = coreModule.eventBus,
        )
    }
}
