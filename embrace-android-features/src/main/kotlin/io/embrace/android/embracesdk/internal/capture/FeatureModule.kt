package io.embrace.android.embracesdk.internal.capture

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
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource

public interface FeatureModule {
    public val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource>
    public val viewDataSource: DataSourceState<ViewDataSource>
    public val pushNotificationDataSource: DataSourceState<PushNotificationDataSource>
    public val tapDataSource: DataSourceState<TapDataSource>
    public val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource>
    public val rnActionDataSource: DataSourceState<RnActionDataSource>
    public val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource>
    public val webViewDataSource: DataSourceState<WebViewDataSource>
    public val lowPowerDataSource: DataSourceState<LowPowerDataSource>
    public val thermalStateDataSource: DataSourceState<ThermalStateDataSource>
    public val applicationExitInfoDataSource: DataSourceState<AeiDataSource>
    public val internalErrorDataSource: DataSourceState<InternalErrorDataSource>
    public val networkStatusDataSource: DataSourceState<NetworkStatusDataSource>

    /**
     * Called by the embrace-android-core module. The implementation of this should add any features
     * to the [EmbraceFeatureRegistry] that are _not_ directly referenced by the
     * embrace-android-core module. As an example, [MemoryWarningDataSource] is not directly
     * referenced anywhere, but [BreadcrumbDataSource] is.
     */
    public fun registerFeatures()
}
