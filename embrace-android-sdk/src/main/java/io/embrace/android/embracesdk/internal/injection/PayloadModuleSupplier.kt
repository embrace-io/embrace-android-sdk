package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService

/**
 * Function that returns an instance of [PayloadModule]. Matches the signature of the constructor for [PayloadModuleImpl]
 */
internal typealias PayloadModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    workerThreadModule: WorkerThreadModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
    sessionPropertiesService: SessionPropertiesService
) -> PayloadModule

internal fun createPayloadModule(
    initModule: InitModule,
    coreModule: CoreModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    workerThreadModule: WorkerThreadModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
    sessionPropertiesService: SessionPropertiesService
): PayloadModule = PayloadModuleImpl(
    initModule,
    coreModule,
    androidServicesModule,
    essentialServiceModule,
    systemServiceModule,
    workerThreadModule,
    nativeModule,
    otelModule,
    anrModule,
    sessionPropertiesService
)
