package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.injection.FeatureModule

class FakeFeatureModule : FeatureModule {
    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> = DataSourceState({ null })
    override val viewDataSource: DataSourceState<ViewDataSource> = DataSourceState({ null })
    override val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource> = DataSourceState({ null })
    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> = DataSourceState({ null })
    override val rnActionDataSource: DataSourceState<RnActionDataSource> = DataSourceState({ null })
    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> = DataSourceState({ null })
}
