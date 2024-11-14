package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [PayloadSourceModule].
 * Matches the signature of the constructor for [PayloadSourceModuleImpl]
 */
typealias PayloadSourceModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    nativeSymbolsProvider: Provider<Map<String, String>?>,
    otelModule: OpenTelemetryModule,
    otelPayloadMapperProvider: Provider<OtelPayloadMapper>,
    deliveryModule: DeliveryModule,
) -> PayloadSourceModule

@Suppress("LongParameterList")
fun createPayloadSourceModule(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    nativeSymbolsProvider: Provider<Map<String, String>?>,
    otelModule: OpenTelemetryModule,
    otelPayloadMapperProvider: Provider<OtelPayloadMapper>,
    deliveryModule: DeliveryModule,
): PayloadSourceModule = PayloadSourceModuleImpl(
    initModule,
    coreModule,
    workerThreadModule,
    systemServiceModule,
    androidServicesModule,
    essentialServiceModule,
    configModule,
    nativeSymbolsProvider,
    otelModule,
    otelPayloadMapperProvider,
    deliveryModule,
)
