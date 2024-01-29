package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.session.BackgroundActivityService

internal class FakeBackgroundActivityService : BackgroundActivityService {

    val endTimestamps = mutableListOf<Long>()
    val startTimestamps = mutableListOf<Long>()
    var crashId: String? = null

    override fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): String {
        startTimestamps.add(timestamp)
        return "fakeBackgroundActivityId"
    }

    override fun endBackgroundActivityWithState(timestamp: Long) {
        endTimestamps.add(timestamp)
    }

    override fun endBackgroundActivityWithCrash(timestamp: Long, crashId: String) {
        this.crashId = crashId
    }

    override fun saveBackgroundActivitySnapshot() {
    }
}
