package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig

class FakeAppExitInfoBehavior(
    private val traceMaxLimit: Int = 20000000,
    private val enabled: Boolean = true,
    private val appExitInfoMaxNum: Int = 0,
) : AppExitInfoBehavior {

    override val local: EnabledFeatureConfig
        get() = throw UnsupportedOperationException()
    override val remote: AppExitInfoConfig
        get() = throw UnsupportedOperationException()

    override fun getTraceMaxLimit(): Int = traceMaxLimit
    override fun isAeiCaptureEnabled(): Boolean = enabled
    override fun appExitInfoMaxNum(): Int = appExitInfoMaxNum
}
