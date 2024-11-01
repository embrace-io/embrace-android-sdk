package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource

interface FeatureModule {
    val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource>
    val viewDataSource: DataSourceState<ViewDataSource>
    val pushNotificationDataSource: DataSourceState<PushNotificationDataSource>
    val tapDataSource: DataSourceState<TapDataSource>
    val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource>
    val rnActionDataSource: DataSourceState<RnActionDataSource>
    val webViewDataSource: DataSourceState<WebViewDataSource>
    val lowPowerDataSource: DataSourceState<LowPowerDataSource>
    val thermalStateDataSource: DataSourceState<ThermalStateDataSource>
    val applicationExitInfoDataSource: DataSourceState<AeiDataSource>
    val internalErrorDataSource: DataSourceState<InternalErrorDataSource>
    val networkStatusDataSource: DataSourceState<NetworkStatusDataSource>

    /**
     * Called by the embrace-android-core module. The implementation of this should add any features
     * to the [EmbraceFeatureRegistry] that are _not_ directly referenced by the
     * embrace-android-core module.
     */
    fun registerFeatures()
}
