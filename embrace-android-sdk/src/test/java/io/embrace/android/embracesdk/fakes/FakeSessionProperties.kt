package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

internal fun fakeEmbraceSessionProperties() = EmbraceSessionProperties(
    FakePreferenceService(),
    FakeConfigService(),
    EmbLoggerImpl()
)
