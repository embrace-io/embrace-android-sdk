package io.embrace.android.embracesdk.compose.internal.instrumentation

import io.embrace.android.embracesdk.compose.ComposeActivityListener
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.instrumentation.TapDataSource
import kotlin.concurrent.Volatile

/**
 * Captures custom breadcrumbs for compose taps.
 */
class ComposeTapDataSource(
    private val args: InstrumentationArgs,
    private val tapDataSourceProvider: () -> TapDataSource?,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy(args.configService.breadcrumbBehavior::getTapBreadcrumbLimit)
) {

    @Volatile
    private var callback: ComposeActivityListener? = null

    fun logComposeTap(coords: Pair<Float, Float>, tag: String) {
        tapDataSourceProvider()?.logComposeTap(coords, tag)
    }

    override fun onDataCaptureEnabled() {
        callback = ComposeActivityListener(logger, this)
        args.application.registerActivityLifecycleCallbacks(callback)
    }

    override fun onDataCaptureDisabled() {
        args.application.unregisterActivityLifecycleCallbacks(callback)
    }
}
