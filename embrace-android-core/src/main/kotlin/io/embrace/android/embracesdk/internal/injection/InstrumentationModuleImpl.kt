package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.InstrumentationArgsImpl
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.storage.StorageService

class InstrumentationModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    coreModule: CoreModule,
    storageService: StorageService,
) : InstrumentationModule {

    override val instrumentationRegistry: InstrumentationRegistry by singleton {
        InstrumentationRegistryImpl(
            initModule.logger
        )
    }

    override val instrumentationArgs: InstrumentationArgs by singleton {
        InstrumentationArgsImpl(
            configService = configModule.configService,
            logger = initModule.logger,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            destination = essentialServiceModule.telemetryDestination,
            workerThreadModule = workerThreadModule,
            store = coreModule.store,
            serializer = initModule.jsonSerializer,
            sessionTracker = essentialServiceModule.sessionTracker,
            ordinalStore = coreModule.ordinalStore,
            sessionPropertiesService = essentialServiceModule.sessionPropertiesService,
            cpuAbi = configModule.cpuAbi,
            processIdentifier = openTelemetryModule.otelSdkConfig.processIdentifier,
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            symbols = configModule.nativeSymbolMap,
            appStateTracker = essentialServiceModule.appStateTracker,
        )
    }
}
