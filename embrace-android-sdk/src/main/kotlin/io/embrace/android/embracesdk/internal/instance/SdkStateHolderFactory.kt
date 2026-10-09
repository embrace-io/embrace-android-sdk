package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.utils.EmbTrace

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    bootstrapper: ModuleInitBootstrapper = EmbTrace.trace(
        sectionName = "bootstrapper-init",
        recordDuration = true,
        code = ::ModuleInitBootstrapper,
    ),
): SdkStateHolder {
    lateinit var holder: SdkStateHolder
    val impl = EmbraceImpl(bootstrapper, { holder.api })
    holder = SdkStateHolder(impl, impl.telemetryService, impl.internalErrorHandler, impl)
    return holder
}
