package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute

class FakeSpanToken(
    val name: String,
    val startTimeMs: Long,
    var endTimeMs: Long?,
    var errorCode: ErrorCodeAttribute?,
    var parent: SpanToken?,
    val type: EmbType,
    val internal: Boolean,
    initialAttrs: Map<String, String>,
    val events: List<SpanEvent>,
) : SpanToken {

    val attributes: Map<String, String>
        get() = attrs.toMap()

    private val attrs: MutableMap<String, String> = initialAttrs.toMutableMap()

    override fun stop(endTimeMs: Long?, errorCode: ErrorCodeAttribute?) {
        this.endTimeMs = endTimeMs ?: 0
        this.errorCode = errorCode
    }

    override fun isRecording(): Boolean = endTimeMs == null

    override fun addAttribute(key: String, value: String) {
        attrs[key] = value
    }

    override fun setSystemAttribute(key: String, value: String) {
    }

    override fun getStartTimeMs(): Long? = startTimeMs
}
