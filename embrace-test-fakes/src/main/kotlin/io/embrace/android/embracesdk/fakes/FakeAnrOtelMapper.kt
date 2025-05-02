package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper

fun fakeAnrOtelMapper(): AnrOtelMapper = AnrOtelMapper(FakeAnrService(), FakeClock(), FakeSpanService())
