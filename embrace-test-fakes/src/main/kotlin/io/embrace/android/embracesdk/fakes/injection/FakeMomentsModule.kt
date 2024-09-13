package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.internal.event.EventService
import io.embrace.android.embracesdk.internal.injection.MomentsModule

class FakeMomentsModule(
    override val eventService: EventService = FakeEventService()
) : MomentsModule
