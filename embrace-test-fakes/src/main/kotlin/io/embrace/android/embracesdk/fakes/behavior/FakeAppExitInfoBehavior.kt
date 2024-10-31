package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior

class FakeAppExitInfoBehavior(
    private val traceMaxLimit: Int = 20000000,
    private val enabled: Boolean = true,
    private val appExitInfoMaxNum: Int = 0,
) : AppExitInfoBehavior {

    override fun getTraceMaxLimit(): Int = traceMaxLimit
    override fun isAeiCaptureEnabled(): Boolean = enabled
    override fun appExitInfoMaxNum(): Int = appExitInfoMaxNum
}
