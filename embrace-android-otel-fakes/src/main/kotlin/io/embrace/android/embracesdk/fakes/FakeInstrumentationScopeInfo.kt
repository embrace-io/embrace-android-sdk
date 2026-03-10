package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.InstrumentationScopeInfo

internal class FakeInstrumentationScopeInfo(
    override val attributes: Map<String, Any> = emptyMap(),
    override val name: String = "fake",
    override val schemaUrl: String? = null,
    override val version: String? = null,
) : InstrumentationScopeInfo
