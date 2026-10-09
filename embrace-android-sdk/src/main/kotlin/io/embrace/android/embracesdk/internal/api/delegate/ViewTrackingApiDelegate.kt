package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.instrumentation.view.ViewDataSource

internal class ViewTrackingApiDelegate(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
    private val sdkCallChecker: SdkCallChecker,
) : ViewTrackingApi {

    private val bootstrapper by lazyBootstrapper

    override fun startView(name: String): Boolean {
        if (sdkCallChecker.check("start_view")) {
            val dataSource = bootstrapper.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
            return dataSource?.startView(name, true) ?: false
        }
        return false
    }

    override fun endView(name: String): Boolean {
        if (sdkCallChecker.check("end_view")) {
            val dataSource = bootstrapper.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
            return dataSource?.endView(name) ?: false
        }
        return false
    }
}
