package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Creates instances of [EmbraceSdkSpan] for internal usage. Using this factory is preferred to invoking the constructor
 * because of the it requires several services that may not be easily available.
 */
interface EmbraceSpanFactory {
    fun create(
        name: String,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        parent: EmbraceSpan? = null,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE
    ): EmbraceSdkSpan

    fun create(
        otelSpanCreator: OtelSpanCreator,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE
    ): EmbraceSdkSpan
}
