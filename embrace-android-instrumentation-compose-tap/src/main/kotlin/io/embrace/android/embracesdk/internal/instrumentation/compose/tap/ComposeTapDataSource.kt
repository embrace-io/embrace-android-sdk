package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal

/**
 * Captures custom breadcrumbs for compose taps.
 */
internal class ComposeTapDataSource(
    private val args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { args.configService.config.breadcrumb.tapLimit },
    instrumentationName = "compose_tap_data_source",
) {

    fun logComposeTap(coords: Pair<Float, Float>, tag: String) {
        args.eventBus.emit(TapSignal.KEY, TapSignal(tag, coords.first, coords.second))
    }

    override fun onDataCaptureEnabled() {
        args.application.registerActivityLifecycleCallbacks(ComposeActivityListener(logger, this))
    }
}
