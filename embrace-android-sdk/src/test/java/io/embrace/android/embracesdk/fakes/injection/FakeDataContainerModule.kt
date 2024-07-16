package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.injection.DataContainerModule
import io.embrace.android.embracesdk.internal.event.EventService

internal class FakeDataContainerModule(
    override val eventService: EventService = FakeEventService()
) : DataContainerModule
