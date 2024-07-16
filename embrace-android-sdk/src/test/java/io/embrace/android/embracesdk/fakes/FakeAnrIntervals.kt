package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.AnrInterval
import io.embrace.android.embracesdk.internal.payload.AnrSample
import io.embrace.android.embracesdk.internal.payload.AnrSampleList
import io.embrace.android.embracesdk.internal.payload.ThreadInfo

internal val fakeAnrIntervalThreads = listOf(
    ThreadInfo(
        5,
        Thread.State.BLOCKED,
        "",
        9,
        lines = listOf("line1", "line2")
    )
)

internal val fakeCompletedAnrInterval = AnrInterval(
    1000,
    null,
    2000,
    anrSampleList = AnrSampleList(
        listOf(
            AnrSample(
                timestamp = 1000,
                sampleOverheadMs = 5,
                threads = fakeAnrIntervalThreads
            ),
        )
    )
)

internal val fakeInProgressAnrInterval =
    fakeCompletedAnrInterval.copy(lastKnownTime = 2000, endTime = null)
