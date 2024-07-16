package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.internal.event.EventService
import io.embrace.android.embracesdk.internal.injection.DataContainerModule

internal class FakeDataContainerModule(
    override val eventService: EventService = FakeEventService()
) : DataContainerModule
