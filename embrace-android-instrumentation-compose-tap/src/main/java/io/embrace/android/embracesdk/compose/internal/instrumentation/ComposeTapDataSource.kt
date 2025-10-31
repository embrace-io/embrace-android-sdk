package io.embrace.android.embracesdk.compose.internal.instrumentation

import android.app.Application
import io.embrace.android.embracesdk.compose.ComposeActivityListener
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.instrumentation.TapDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import kotlin.concurrent.Volatile

/**
 * Captures custom breadcrumbs for compose taps.
 */
class ComposeTapDataSource(
    private val app: Application,
    private val breadcrumbBehavior: BreadcrumbBehavior,
    destination: TelemetryDestination,
    logger: EmbLogger,
    private val tapDataSourceProvider: () -> TapDataSource?,
) : DataSourceImpl(
    destination = destination,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getTapBreadcrumbLimit)
) {

    @Volatile
    private var callback: ComposeActivityListener? = null

    fun logComposeTap(coords: Pair<Float, Float>, tag: String) {
        tapDataSourceProvider()?.logComposeTap(coords, tag)
    }

    override fun onDataCaptureEnabled() {
        callback = ComposeActivityListener(logger, this)
        app.registerActivityLifecycleCallbacks(callback)
    }

    override fun onDataCaptureDisabled() {
        app.unregisterActivityLifecycleCallbacks(callback)
    }
}
