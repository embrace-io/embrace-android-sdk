package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService

/**
 * Contains dependencies that are required for delivering code to Embrace servers.
 *
 * Types are nullable because folks who are only exporting via OTel exporters do not need this
 * functionality.
 */
interface DeliveryModule2 {
    val intakeService: IntakeService?
    val payloadResurrectionService: PayloadResurrectionService?
    val payloadCachingService: PayloadCachingService?
    val payloadStorageService: PayloadStorageService?
    val requestExecutionService: RequestExecutionService?
    val schedulingService: SchedulingService?
}
