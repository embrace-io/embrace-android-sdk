package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Creates instances of [PersistableEmbraceSpan] for internal usage. Using this factory is preferred to invoking the constructor
 * because of the it requires several services that may not be easily available.
 */
public interface EmbraceSpanFactory {
    public fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        parent: EmbraceSpan? = null
    ): PersistableEmbraceSpan

    public fun create(embraceSpanBuilder: EmbraceSpanBuilder): PersistableEmbraceSpan
}
