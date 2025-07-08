package io.embrace.android.embracesdk.internal.injection

fun createOpenTelemetryModule(initModule: InitModule): OpenTelemetryModule =
    OpenTelemetryModuleImpl(initModule)
