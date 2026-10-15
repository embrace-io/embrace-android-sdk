package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.injection.BootstrapServices
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    bootstrapServices: BootstrapServices = BootstrapServices(),
    bootstrapperProvider: Provider<ModuleInitBootstrapper> = {
        EmbTrace.trace(sectionName = "bootstrapper-init", recordDuration = true) {
            val initModule = EmbTrace.trace("init-module") {
                InitModuleImpl(bootstrapServices)
            }
            ModuleInitBootstrapper(
                initModule = initModule,
                openTelemetryModule = EmbTrace.trace("otel-module") {
                    OpenTelemetryModuleImpl(initModule, bootstrapServices.externalOtelConfig)
                },
            )
        }
    },
): SdkStateHolder {
    val logger = bootstrapServices.logger
    val clock = bootstrapServices.clock
    var holder: SdkStateHolder? = null
    holder = SdkStateHolder(
        instance = {
            RealSdkInstance(
                bootstrapper = bootstrapperProvider(),
                hostedSdkApiProvider = { checkNotNull(holder?.api) },
                clock = clock,
            )
        },
        preStart = {
            BufferingSdkInstance(
                clock = clock::now,
                logger = logger,
            )
        },
        disabled = { NoopSdkInstance(logger, NoopSdkInstance.SDK_DISABLED) },
        telemetryService = bootstrapServices.telemetryService,
        errorHandler = logger,
    )
    return holder
}
