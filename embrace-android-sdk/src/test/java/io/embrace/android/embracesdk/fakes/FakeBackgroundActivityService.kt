package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.session.BackgroundActivityService

internal class FakeBackgroundActivityService : BackgroundActivityService {

    val endTimestamps = mutableListOf<Long>()
    val startTimestamps = mutableListOf<Long>()

    override fun startBackgroundActivityWithState(coldStart: Boolean, timestamp: Long) {
        startTimestamps.add(timestamp)
    }

    override fun endBackgroundActivityWithState(timestamp: Long) {
        endTimestamps.add(timestamp)
    }

    override fun endBackgroundActivityWithCrash(crashId: String) {
    }

    override fun save() {
        TODO("Not yet implemented")
    }
}
