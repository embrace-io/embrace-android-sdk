package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer

@OptIn(ExperimentalApi::class)
class FakeAttributeContainer(
    private val impl: MutableMap<String, Any> = mutableMapOf(),
) : AttributeContainer {

    override fun attributes(): Map<String, Any> = impl.toMap()

    override fun setBooleanAttribute(key: String, value: Boolean) {
        impl[key] = value
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        impl[key] = value
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        impl[key] = value
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        impl[key] = value
    }

    override fun setLongAttribute(key: String, value: Long) {
        impl[key] = value
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        impl[key] = value
    }

    override fun setStringAttribute(key: String, value: String) {
        impl[key] = value
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        impl[key] = value
    }
}
