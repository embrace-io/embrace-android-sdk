package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService

/**
 * Contains dependencies that are required for delivering code to Embrace servers.
 */
interface DeliveryModule2 {
    val intakeService: IntakeService
    val payloadResurrectionService: PayloadResurrectionService
    val payloadCachingService: PayloadCachingService
    val requestExecutionService: RequestExecutionService
    val schedulingService: SchedulingService
}
