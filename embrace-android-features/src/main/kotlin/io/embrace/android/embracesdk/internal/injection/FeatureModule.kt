package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource

interface FeatureModule {
    val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource>
    val viewDataSource: DataSourceState<ViewDataSource>
    val pushNotificationDataSource: DataSourceState<PushNotificationDataSource>
    val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource>
    val rnActionDataSource: DataSourceState<RnActionDataSource>
    val lowPowerDataSource: DataSourceState<LowPowerDataSource>
    val thermalStateDataSource: DataSourceState<ThermalStateDataSource>
    val applicationExitInfoDataSource: DataSourceState<AeiDataSource>
    val internalErrorDataSource: DataSourceState<InternalErrorDataSource>
    val networkStatusDataSource: DataSourceState<NetworkStatusDataSource>
}
