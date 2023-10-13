package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.payload.Event
import java.util.concurrent.Future

internal data class EventDescription(
    val lateTimer: Future<*>,
    val event: Event
)
