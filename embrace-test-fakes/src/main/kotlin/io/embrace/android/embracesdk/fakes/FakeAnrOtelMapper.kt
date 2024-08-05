package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer

public fun fakeAnrOtelMapper(): AnrOtelMapper = AnrOtelMapper(FakeAnrService(), FakeClock())
public fun fakeNativeAnrOtelMapper(): NativeAnrOtelMapper =
    NativeAnrOtelMapper(null, EmbraceSerializer(), FakeClock())
