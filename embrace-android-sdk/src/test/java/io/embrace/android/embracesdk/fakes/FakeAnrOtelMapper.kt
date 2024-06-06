package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer

internal fun fakeAnrOtelMapper() = AnrOtelMapper(FakeAnrService(), FakeClock())
internal fun fakeNativeAnrOtelMapper() = NativeAnrOtelMapper(null, EmbraceSerializer(), FakeClock())
