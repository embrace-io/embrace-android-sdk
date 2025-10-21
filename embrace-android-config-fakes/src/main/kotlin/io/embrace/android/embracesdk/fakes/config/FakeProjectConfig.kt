package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.ProjectConfig

class FakeProjectConfig(
    base: ProjectConfig = InstrumentedConfigImpl.project,
    private val appId: String? = base.getAppId(),
    private val appFramework: String? = base.getAppFramework(),
    private val buildId: String? = base.getBuildId(),
    private val buildType: String? = base.getBuildType(),
    private val buildFlavor: String? = base.getBuildFlavor(),
) : ProjectConfig {
    override fun getAppId(): String? = appId
    override fun getAppFramework(): String? = appFramework
    override fun getBuildId(): String? = buildId
    override fun getBuildType(): String? = buildType
    override fun getBuildFlavor(): String? = buildFlavor
}
