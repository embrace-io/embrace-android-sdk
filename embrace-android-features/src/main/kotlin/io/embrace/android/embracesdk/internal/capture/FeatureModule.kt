package io.embrace.android.embracesdk.internal.capture

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.memory.MemoryWarningDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.singleton

public class FeatureModule(
    coreModule: CoreModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    configService: ConfigService
) {
    public val memoryWarningDataSource: DataSourceState<MemoryWarningDataSource> by singleton {
        DataSourceState(
            factory = {
                MemoryWarningDataSource(
                    application = coreModule.application,
                    clock = initModule.clock,
                    sessionSpanWriter = otelModule.currentSessionSpan,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isMemoryServiceEnabled() },
        )
    }

    public val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by singleton {
        DataSourceState(
            factory = {
                BreadcrumbDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
        )
    }
}
