package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.schema.EmbType

class FakeSpanToken(
    val name: String,
    val startTimeMs: Long,
    var endTimeMs: Long?,
    var errorCode: String?,
    val type: EmbType,
    val attributes: Map<String, String>,
) : SpanToken {
    override fun stop(endTimeMs: Long?) {
        this.endTimeMs = endTimeMs ?: 0
    }

    fun isRecording(): Boolean = endTimeMs == null
}
