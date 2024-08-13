package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [PayloadModule]. Matches the signature of the constructor for [PayloadModuleImpl]
 */
internal typealias PayloadModuleSupplier = (
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
) -> PayloadModule

internal fun createPayloadModule(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
): PayloadModule = PayloadModuleImpl(
    initModule,
    essentialServiceModule,
    nativeModule,
    otelModule,
    anrModule,
)
