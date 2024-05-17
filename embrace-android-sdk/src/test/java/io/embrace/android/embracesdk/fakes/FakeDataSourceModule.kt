package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.capture.memory.MemoryWarningDataSource
import io.embrace.android.embracesdk.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.injection.DataSourceModule

internal class FakeDataSourceModule : DataSourceModule {

    override fun getDataSources(): List<DataSourceState<*>> = emptyList()

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> = DataSourceState({ null })
    override val viewDataSource: DataSourceState<ViewDataSource> = DataSourceState({ null })
    override val tapDataSource: DataSourceState<TapDataSource> = DataSourceState({ null })
    override val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource> = DataSourceState({ null })
    override val pushNotificationDataSource: DataSourceState<PushNotificationDataSource> = DataSourceState({ null })
    override val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource> = DataSourceState({ null })
    override val applicationExitInfoDataSource: DataSourceState<AeiDataSource> = DataSourceState({ null })
    override val lowPowerDataSource: DataSourceState<LowPowerDataSource> = DataSourceState({ null })
    override val memoryWarningDataSource: DataSourceState<MemoryWarningDataSource> = DataSourceState({ null })
    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> = DataSourceState({ null })
    override val sigquitDataSource: DataSourceState<SigquitDataSource> = DataSourceState({ null })
    override val rnActionDataSource: DataSourceState<RnActionDataSource> = DataSourceState({ null })
    override val thermalStateDataSource: DataSourceState<ThermalStateDataSource> = DataSourceState({ null })
}
