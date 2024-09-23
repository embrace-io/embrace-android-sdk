package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakePayloadCachingService
import io.embrace.android.embracesdk.fakes.FakePayloadResurrectionService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.injection.DeliveryModule2

class FakeDeliveryModule2(
    override val intakeService: IntakeService = FakeIntakeService(),
    override val payloadResurrectionService: PayloadResurrectionService = FakePayloadResurrectionService(),
    override val payloadCachingService: PayloadCachingService = FakePayloadCachingService(),
    override val requestExecutionService: RequestExecutionService = FakeRequestExecutionService(),
    override val schedulingService: SchedulingService = FakeSchedulingService(),
) : DeliveryModule2
