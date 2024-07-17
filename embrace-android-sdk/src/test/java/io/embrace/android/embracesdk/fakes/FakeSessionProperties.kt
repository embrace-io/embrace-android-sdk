package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.properties.EmbraceSessionProperties

internal fun fakeEmbraceSessionProperties() = EmbraceSessionProperties(
    FakePreferenceService(),
    FakeConfigService(),
    EmbLoggerImpl()
)
