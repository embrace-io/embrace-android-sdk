package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore

/**
 * Contains dependencies that are required for delivering code to Embrace servers.
 *
 * Types are nullable because folks who are only exporting via OTel exporters do not need this
 * functionality.
 */
interface DeliveryModule {
    val payloadStore: PayloadStore?
    val deliveryService: DeliveryService?
    val intakeService: IntakeService?
    val payloadCachingService: PayloadCachingService?
    val payloadStorageService: PayloadStorageService?
    val cacheStorageService: PayloadStorageService?
    val requestExecutionService: RequestExecutionService?
    val schedulingService: SchedulingService?
    val deliveryTracer: DeliveryTracer?
}
