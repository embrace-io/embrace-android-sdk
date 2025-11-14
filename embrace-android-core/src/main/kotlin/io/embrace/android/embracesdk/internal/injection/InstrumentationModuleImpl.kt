package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.InstrumentationArgsImpl
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl

internal class InstrumentationModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    coreModule: CoreModule,
) : InstrumentationModule {

    override val instrumentationRegistry: InstrumentationRegistry by singleton {
        InstrumentationRegistryImpl(
            initModule.logger
        )
    }

    override val instrumentationArgs by singleton {
        InstrumentationArgsImpl(
            configService = configModule.configService,
            logger = initModule.logger,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            destination = essentialServiceModule.telemetryDestination,
            workerThreadModule = workerThreadModule,
            store = androidServicesModule.store,
            serializer = initModule.jsonSerializer,
            sessionIdTracker = essentialServiceModule.sessionIdTracker,
            ordinalStore = androidServicesModule.ordinalStore,
            sessionPropertiesService = essentialServiceModule.sessionPropertiesService,
        )
    }
}
