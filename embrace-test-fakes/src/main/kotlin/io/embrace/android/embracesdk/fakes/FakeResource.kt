package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.resource.Resource

@OptIn(ExperimentalApi::class)
internal class FakeResource(
    override val attributes: Map<String, Any> = emptyMap(),
    override val schemaUrl: String? = null,
) : Resource
