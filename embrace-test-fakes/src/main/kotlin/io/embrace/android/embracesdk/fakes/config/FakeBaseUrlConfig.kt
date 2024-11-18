package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.BaseUrlConfig

class FakeBaseUrlConfig(
    base: BaseUrlConfig = InstrumentedConfigImpl.baseUrls,
    private val configImpl: String? = base.getConfig(),
    private val dataImpl: String? = base.getData(),
) : BaseUrlConfig {
    override fun getConfig(): String? = configImpl
    override fun getData(): String? = dataImpl
}
