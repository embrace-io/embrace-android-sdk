package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrOtelMapper

fun fakeAnrOtelMapper(): OtelPayloadMapper = AnrOtelMapper(
    FakeAnrService(),
    FakeClock(),
    FakeTelemetryDestination()
)
