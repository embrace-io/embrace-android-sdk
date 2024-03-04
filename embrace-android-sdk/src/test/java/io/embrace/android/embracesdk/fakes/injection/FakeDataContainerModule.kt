package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.injection.DataContainerModule

internal class FakeDataContainerModule(
    override val eventService: EventService = FakeEventService(),
    override val performanceInfoService: PerformanceInfoService = FakePerformanceInfoService()
) : DataContainerModule
