package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionServiceImpl
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionServiceImpl
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl
import io.embrace.android.embracesdk.internal.injection.singleton

internal class DeliveryModule2Impl : DeliveryModule2 {

    override val intakeService: IntakeService by singleton {
        IntakeServiceImpl(schedulingService)
    }

    override val payloadResurrectionService: PayloadResurrectionService by singleton {
        PayloadResurrectionServiceImpl(intakeService)
    }

    override val payloadCachingService: PayloadCachingService by singleton {
        PayloadCachingServiceImpl()
    }

    override val requestExecutionService: RequestExecutionService by singleton {
        RequestExecutionServiceImpl()
    }

    override val schedulingService: SchedulingService by singleton {
        SchedulingServiceImpl(requestExecutionService)
    }
}
