package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.EmbraceSessionProperties

internal fun fakeEmbraceSessionProperties() = EmbraceSessionProperties(
    FakePreferenceService(),
    InternalEmbraceLogger(),
    FakeConfigService()
)
