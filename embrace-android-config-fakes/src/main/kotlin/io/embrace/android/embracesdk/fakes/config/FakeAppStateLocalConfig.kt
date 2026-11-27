package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.schema.AppStateLocalConfig

class FakeAppStateLocalConfig(
    private val enabled: Boolean = false,
    private val exportMode: String = "",
) : AppStateLocalConfig {
    override fun isEnabled(): Boolean = enabled
    override fun getExportMode(): String = exportMode
}
