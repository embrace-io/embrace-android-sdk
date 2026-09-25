package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.TapDataSource

/**
 * Captures custom breadcrumbs for compose taps.
 */
internal class ComposeTapDataSource(
    private val args: InstrumentationArgs,
    private val tapDataSourceProvider: () -> TapDataSource?,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy(args.configService.breadcrumbBehavior::getTapBreadcrumbLimit),
    instrumentationName = "compose_tap_data_source",
) {

    fun logComposeTap(coords: Pair<Float, Float>, tag: String) {
        captureTelemetry {
            tapDataSourceProvider()?.logComposeTap(coords, tag)
        }
    }

    override fun onDataCaptureEnabled() {
        args.application.registerActivityLifecycleCallbacks(ComposeActivityListener(logger, this))
    }
}
