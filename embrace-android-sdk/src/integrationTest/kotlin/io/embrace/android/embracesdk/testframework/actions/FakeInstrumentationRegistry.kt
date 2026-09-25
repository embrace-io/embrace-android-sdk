package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry

internal class FakeInstrumentationRegistry(
    private val impl: InstrumentationRegistry,
) : InstrumentationRegistry by impl
