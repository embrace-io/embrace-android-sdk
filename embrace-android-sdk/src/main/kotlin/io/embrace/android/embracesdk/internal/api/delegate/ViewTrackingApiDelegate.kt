package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.instrumentation.view.ViewDataSource

internal class ViewTrackingApiDelegate(
    private val bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : ViewTrackingApi {

    override fun startView(name: String): Boolean {
        if (sdkCallChecker.check("start_view")) {
            val dataSource = bootstrapper.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
            return dataSource?.startView(name) ?: false
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

    @Deprecated("This function has no effect.")
    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String,
    ) {
    }

    @Deprecated("This function has no effect.")
    override fun logRnView(screen: String) {
    }
}
