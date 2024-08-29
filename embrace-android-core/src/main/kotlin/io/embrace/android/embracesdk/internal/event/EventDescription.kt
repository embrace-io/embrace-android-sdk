package io.embrace.android.embracesdk.internal.event

import io.embrace.android.embracesdk.internal.payload.Event
import java.util.concurrent.Future

internal data class EventDescription(
    val lateTimer: Future<*>,
    val event: Event
)
