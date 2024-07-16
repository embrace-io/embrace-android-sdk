package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.memory.MemoryWarningDataSource
import io.embrace.android.embracesdk.internal.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorDataSource

/**
 * Declares all the data sources that are used by the Embrace SDK.
 *
 * To add a new data source, simply define a new property of type [DataSourceState] using
 * the [dataSourceState] property delegate. It is important that you use this delegate as otherwise
 * the property won't be propagated to the [DataCaptureOrchestrator].
 *
 * Data will then automatically be captured by the SDK.
 */
internal interface DataSourceModule {
    val embraceFeatureRegistry: EmbraceFeatureRegistry
    val dataCaptureOrchestrator: DataCaptureOrchestrator
    val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource>
    val viewDataSource: DataSourceState<ViewDataSource>
    val tapDataSource: DataSourceState<TapDataSource>
    val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource>
    val pushNotificationDataSource: DataSourceState<PushNotificationDataSource>
    val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource>
    val applicationExitInfoDataSource: DataSourceState<AeiDataSource>?
    val lowPowerDataSource: DataSourceState<LowPowerDataSource>
    val memoryWarningDataSource: DataSourceState<MemoryWarningDataSource>
    val networkStatusDataSource: DataSourceState<NetworkStatusDataSource>
    val sigquitDataSource: DataSourceState<SigquitDataSource>
    val rnActionDataSource: DataSourceState<RnActionDataSource>
    val thermalStateDataSource: DataSourceState<ThermalStateDataSource>?
    val webViewDataSource: DataSourceState<WebViewDataSource>
    val internalErrorDataSource: DataSourceState<InternalErrorDataSource>
}
