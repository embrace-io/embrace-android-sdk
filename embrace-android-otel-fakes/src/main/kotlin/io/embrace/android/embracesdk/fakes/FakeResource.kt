package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.resource.MutableResource
import io.opentelemetry.kotlin.resource.Resource

@OptIn(ExperimentalApi::class)
internal class FakeResource(
    override val attributes: Map<String, Any> = emptyMap(),
    override val schemaUrl: String? = null,
) : Resource {
    override fun asNewResource(action: MutableResource.() -> Unit): Resource = this
}
