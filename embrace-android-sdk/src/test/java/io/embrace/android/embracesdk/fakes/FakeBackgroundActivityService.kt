package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.fakeBackgroundActivityMessage
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.BackgroundActivityService

internal class FakeBackgroundActivityService : BackgroundActivityService {

    val endTimestamps = mutableListOf<Long>()
    val startTimestamps = mutableListOf<Long>()
    var crashId: String? = null
    var snapshotCount: Int = 0

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

    override fun snapshotBackgroundActivity(initial: Session, timestamp: Long): SessionMessage {
        snapshotCount++
        return fakeBackgroundActivityMessage()
    }
}
