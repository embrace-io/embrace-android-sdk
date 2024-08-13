package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [PayloadSourceModule]. Matches the signature of the constructor for [PayloadSourceModuleImpl]
 */
internal typealias PayloadSourceModuleSupplier = (
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
) -> PayloadSourceModule

internal fun createPayloadSourceModule(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
): PayloadSourceModule = PayloadSourceModuleImpl(
    initModule,
    essentialServiceModule,
    nativeModule,
    otelModule,
    anrModule,
)
