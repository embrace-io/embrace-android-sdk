package io.embrace.android.embracesdk.internal.buildinfo

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

internal class BuildInfoServiceImpl(
    private val instrumentedConfig: InstrumentedConfig,
) : BuildInfoService {

    private val info by lazy {
        BuildInfo(
            instrumentedConfig.project.getBuildId(),
            instrumentedConfig.project.getBuildType(),
            instrumentedConfig.project.getBuildFlavor(),
            instrumentedConfig.project.getReactNativeBundleId(),
        )
    }

    override fun getBuildInfo(): BuildInfo = info
}
