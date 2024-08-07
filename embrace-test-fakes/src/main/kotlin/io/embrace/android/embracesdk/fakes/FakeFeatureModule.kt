package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
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
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource
import io.embrace.android.embracesdk.internal.injection.FeatureModule

public class FakeFeatureModule : FeatureModule {
    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> = DataSourceState({ null })
    override val viewDataSource: DataSourceState<ViewDataSource> = DataSourceState({ null })
    override val tapDataSource: DataSourceState<TapDataSource> = DataSourceState({ null })
    override val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource> = DataSourceState({ null })
    override val pushNotificationDataSource: DataSourceState<PushNotificationDataSource> = DataSourceState({ null })
    override val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource> = DataSourceState({ null })
    override val applicationExitInfoDataSource: DataSourceState<AeiDataSource> = DataSourceState({ null })
    override val lowPowerDataSource: DataSourceState<LowPowerDataSource> = DataSourceState({ null })
    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> = DataSourceState({ null })
    override val rnActionDataSource: DataSourceState<RnActionDataSource> = DataSourceState({ null })
    override val thermalStateDataSource: DataSourceState<ThermalStateDataSource> = DataSourceState({ null })
    override val webViewDataSource: DataSourceState<WebViewDataSource> = DataSourceState({ null })
    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> = DataSourceState({ null })
    override val sigquitDataSource: DataSourceState<SigquitDataSource> = DataSourceState({ null })
    override fun registerFeatures() {}
}
