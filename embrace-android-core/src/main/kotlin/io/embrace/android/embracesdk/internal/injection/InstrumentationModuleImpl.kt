package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.InstrumentationArgsImpl
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl

class InstrumentationModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    coreModule: CoreModule,
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
            sessionIdTracker = essentialServiceModule.sessionIdTracker,
            ordinalStore = coreModule.ordinalStore,
            sessionPropertiesService = essentialServiceModule.sessionPropertiesService,
        )
    }
}
