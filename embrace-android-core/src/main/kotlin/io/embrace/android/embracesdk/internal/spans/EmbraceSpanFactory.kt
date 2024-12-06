package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Creates instances of [PersistableEmbraceSpan] for internal usage. Using this factory is preferred to invoking the constructor
 * because of the it requires several services that may not be easily available.
 */
internal interface EmbraceSpanFactory {
    fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
        parent: EmbraceSpan? = null,
    ): PersistableEmbraceSpan

    fun create(embraceSpanBuilder: EmbraceSpanBuilder): PersistableEmbraceSpan

    fun setupSensitiveKeysBehavior(sensitiveKeysBehavior: SensitiveKeysBehavior)
}
