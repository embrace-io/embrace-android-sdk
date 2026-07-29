package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData

class FakeExperimentTrackingService : ExperimentTrackingService {

    val trackedData: MutableList<TrackedData> = mutableListOf()
    val untrackCalls: MutableList<UntrackCall> = mutableListOf()
    var fakeRecords: String? = null

    data class UntrackCall(
        val ids: List<String>,
        val endTimeMs: Long,
    )

    override fun track(data: List<TrackedData>) {
        trackedData.addAll(data)
    }

    override fun untrack(ids: List<String>, endTimeMs: Long) {
        untrackCalls.add(UntrackCall(ids, endTimeMs))
    }

    override fun getRecords(): String? = fakeRecords
}
