package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import java.util.concurrent.atomic.AtomicInteger

class FakeExperimentTrackingService : ExperimentTrackingService {

    val trackedData: MutableList<TrackedData> = mutableListOf()
    val untrackCalls: MutableList<UntrackCall> = mutableListOf()
    val bulkApiCalls: MutableList<ExperimentApiCall> = mutableListOf()
    var fakeRecords: String? = null
    val serviceInvocations: Int
        get() = callCount.get()

    private val callCount = AtomicInteger(0)

    data class UntrackCall(
        val kind: ExperimentKind,
        val ids: List<String>,
        val endTimeMs: Long,
    )

    override fun track(data: List<TrackedData>) {
        trackInternal(data)
        callCount.incrementAndGet()
    }

    override fun untrack(kind: ExperimentKind, ids: List<String>, endTimeMs: Long) {
        untrackInternal(kind, ids, endTimeMs)
        callCount.incrementAndGet()
    }

    override fun bulkModify(events: List<ExperimentApiCall>) {
        bulkApiCalls.addAll(events)
        events.forEach { event ->
            when (event) {
                is ExperimentApiCall.Track -> trackInternal(event.data)
                is ExperimentApiCall.Untrack -> untrackInternal(event.kind, event.ids, event.endTimeMs)
            }
        }
        callCount.incrementAndGet()
    }

    override fun getRecords(): String? = fakeRecords

    private fun trackInternal(data: List<TrackedData>) {
        trackedData.addAll(data)
    }

    private fun untrackInternal(kind: ExperimentKind, ids: List<String>, endTimeMs: Long) {
        untrackCalls.add(UntrackCall(kind, ids, endTimeMs))
    }
}
