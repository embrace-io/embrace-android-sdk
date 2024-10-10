package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock

internal class EmbracePreSdkStartInterface(
    private val setup: EmbraceSetupInterface,
) {
    val embrace = Embrace.getInstance()

    val clock: FakeClock
        get() = setup.overriddenClock
}