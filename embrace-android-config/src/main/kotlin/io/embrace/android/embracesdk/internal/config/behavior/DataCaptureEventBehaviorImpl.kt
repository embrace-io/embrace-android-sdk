package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.PatternCache
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.DataCaptureEventConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveDataCaptureEvent

class DataCaptureEventBehaviorImpl(private val config: DataCaptureEventConfig) : DataCaptureEventBehavior {

    constructor(remote: RemoteConfig?) : this(resolveDataCaptureEvent(behaviorInputs(InstrumentedConfigImpl, remote)))

    private val patternCache = PatternCache()

    override fun isInternalExceptionCaptureEnabled(): Boolean = config.internalExceptionCaptureEnabled

    override fun isEventEnabled(eventName: String): Boolean = isEnabled(eventName)

    override fun isLogMessageEnabled(logMessage: String): Boolean = isEnabled(logMessage)

    private fun isEnabled(value: String): Boolean {
        val disabledPatterns = config.disabledEventAndLogPatterns ?: return true
        return !patternCache.doesStringMatchPatternInSet(value, disabledPatterns)
    }
}
