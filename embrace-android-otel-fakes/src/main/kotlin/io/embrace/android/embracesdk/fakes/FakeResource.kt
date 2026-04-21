package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.resource.MutableResource
import io.opentelemetry.kotlin.resource.Resource

internal class FakeResource(
    override val attributes: Map<String, Any> = emptyMap(),
    override val schemaUrl: String? = null,
) : Resource {
    override fun asNewResource(action: MutableResource.() -> Unit): Resource = this
    override fun merge(other: Resource): Resource = this
}
