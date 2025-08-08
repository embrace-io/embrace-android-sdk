package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalApi::class)
internal class EmbMutableAttributeContainer(
    private val map: MutableMap<String, String> = ConcurrentHashMap(),
) : MutableAttributeContainer {

    override val attributes: Map<String, String>
        get() = map.toMap()

    override fun setBooleanAttribute(key: String, value: Boolean) {
        map[key] = value.toString()
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        map[key] = value.toString()
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        map[key] = value.toString()
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        map[key] = value.toString()
    }

    override fun setLongAttribute(key: String, value: Long) {
        map[key] = value.toString()
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        map[key] = value.toString()
    }

    override fun setStringAttribute(key: String, value: String) {
        map[key] = value
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        map[key] = value.toString()
    }
}
