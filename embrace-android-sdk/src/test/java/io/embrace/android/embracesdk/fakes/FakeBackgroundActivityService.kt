package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.BackgroundActivityService

internal class FakeBackgroundActivityService : BackgroundActivityService {

    val endTimestamps = mutableListOf<Long>()
    val startTimestamps = mutableListOf<Long>()
    var crashId: String? = null

    override fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): Session {
        startTimestamps.add(timestamp)
        return fakeBackgroundActivity()
    }

    override fun endBackgroundActivityWithState(initial: Session, timestamp: Long) {
        endTimestamps.add(timestamp)
    }

    override fun endBackgroundActivityWithCrash(initial: Session, timestamp: Long, crashId: String) {
        this.crashId = crashId
    }

    override fun saveBackgroundActivitySnapshot(initial: Session) {
    }
}
