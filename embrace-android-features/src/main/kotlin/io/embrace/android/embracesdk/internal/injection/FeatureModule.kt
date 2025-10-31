package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource

interface FeatureModule {
    val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource>
    val viewDataSource: DataSourceState<ViewDataSource>
    val rnActionDataSource: DataSourceState<RnActionDataSource>
    val internalErrorDataSource: DataSourceState<InternalErrorDataSource>
    val networkStatusDataSource: DataSourceState<NetworkStatusDataSource>
}
