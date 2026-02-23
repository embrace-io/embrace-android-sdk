package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer

@OptIn(ExperimentalApi::class)
class FakeMutableAttributeContainer(
    override val attributes: MutableMap<String, Any> = mutableMapOf(),
) : MutableAttributeContainer {

    override fun setBooleanAttribute(key: String, value: Boolean) {
        attributes[key] = value
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        attributes[key] = value
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        attributes[key] = value
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        attributes[key] = value
    }

    override fun setLongAttribute(key: String, value: Long) {
        attributes[key] = value
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        attributes[key] = value
    }

    override fun setStringAttribute(key: String, value: String) {
        attributes[key] = value
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        attributes[key] = value
    }
}
