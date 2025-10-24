package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.InstrumentationScopeInfo

@OptIn(ExperimentalApi::class)
internal class FakeInstrumentationScopeInfo(
    override val attributes: Map<String, Any> = emptyMap(),
    override val name: String = "fake",
    override val schemaUrl: String? = null,
    override val version: String? = null,
) : InstrumentationScopeInfo
