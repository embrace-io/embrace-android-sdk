package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl

internal fun fakeEmbraceSessionProperties() = EmbraceSessionProperties(
    FakePreferenceService(),
    FakeConfigService(),
    EmbLoggerImpl()
)
