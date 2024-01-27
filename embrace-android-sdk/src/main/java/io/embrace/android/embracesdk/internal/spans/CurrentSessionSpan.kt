package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan

internal interface CurrentSessionSpan {

    fun startInitialSession(sdkInitStartTimeNanos: Long)

    fun endSession(appTerminationCause: EmbraceAttributes.AppTerminationCause? = null): List<EmbraceSpanData>

    fun completedSpans(): List<EmbraceSpanData>

    fun validateAndUpdateContext(parent: EmbraceSpan?, internal: Boolean): Boolean
}
