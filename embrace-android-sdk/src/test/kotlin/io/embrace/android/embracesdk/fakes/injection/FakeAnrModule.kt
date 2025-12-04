package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService

class FakeAnrModule(
    override val anrService: AnrService? = null
) : AnrModule
