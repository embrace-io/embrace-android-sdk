package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.capture.experiment.UntrackedData

class FakeExperimentTrackingService : ExperimentTrackingService {

    val trackCalls: MutableList<List<TrackedData>> = mutableListOf()
    val untrackCalls: MutableList<List<UntrackedData>> = mutableListOf()

    var fakeRecords: String? = null

    val trackedData: List<TrackedData> get() = trackCalls.flatten()
    val untrackedData: List<UntrackedData> get() = untrackCalls.flatten()

    override fun track(data: List<TrackedData>) {
        trackCalls.add(data)
    }

    override fun untrack(data: List<UntrackedData>) {
        untrackCalls.add(data)
    }

    override fun getRecords(): String? = fakeRecords
}
