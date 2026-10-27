package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

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

    @Volatile
    private var activityListener: ComposeActivityListener? = null

    override fun onDataCaptureEnabled() {
        val listener = ComposeActivityListener(errorHandler, this)
        activityListener = listener
        args.application.registerActivityLifecycleCallbacks(listener)
    }

    override fun onDataCaptureDisabled() {
        // each step is guarded on its own, so that one failing does not leave the other in place
        activityListener?.let { listener ->
            errorHandler.guardSdkCode(SdkErrorType.InstrumentationDisableFail) {
                args.application.unregisterActivityLifecycleCallbacks(listener)
            }
            errorHandler.guardSdkCode(SdkErrorType.InstrumentationDisableFail, listener::close)
        }
        activityListener = null
    }
}
