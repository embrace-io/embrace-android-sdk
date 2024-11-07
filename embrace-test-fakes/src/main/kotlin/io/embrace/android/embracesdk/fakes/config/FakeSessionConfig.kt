package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.SessionConfig

class FakeSessionConfig(
    base: SessionConfig = InstrumentedConfigImpl.session,
    private val sessionComponentsImpl: List<String>? = base.getSessionComponents(),
    private val fullSessionEventsImpl: List<String> = base.getFullSessionEvents(),
) : SessionConfig {
    override fun getSessionComponents(): List<String>? = sessionComponentsImpl
    override fun getFullSessionEvents(): List<String> = fullSessionEventsImpl
}
