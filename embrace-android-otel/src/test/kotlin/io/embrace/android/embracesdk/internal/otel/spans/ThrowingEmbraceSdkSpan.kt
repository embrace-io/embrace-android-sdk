package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.spans.ErrorCode

internal class ThrowingEmbraceSdkSpan(
    delegate: FakeEmbraceSdkSpan,
) : EmbraceSdkSpan by delegate {
    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean = error("stop failed")

    override fun stopWithErrorCode(errorCode: ErrorCodeAttribute?, endTimeMs: Long?): Boolean = error("stop failed")
}
