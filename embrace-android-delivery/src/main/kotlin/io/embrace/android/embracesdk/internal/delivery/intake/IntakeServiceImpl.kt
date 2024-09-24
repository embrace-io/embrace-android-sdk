package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.TaskPriority

@Suppress("unused")
internal class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<TaskPriority>,
    private val storageLimit: Int = 100,
    private val shutdownTimeoutMs: Long = 3000
) : IntakeService {

    override fun handleCrash(crashId: String) {
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
    }
}
