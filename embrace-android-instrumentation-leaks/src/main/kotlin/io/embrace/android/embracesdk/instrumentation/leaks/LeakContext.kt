package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

/**
 * Everything reporting a leak needs about a tracked object other than the object itself. Passed to
 * [LeakDetector.trackClosed] as its token, and handed back, unchanged, as [LeakDetector.LeakSnapshot.token] if that object
 * is confirmed as a leak.
 *
 * [objectType] is supplied by whichever instrumentation tracked the object rather than decided when the leak is reported,
 * so that support for a new kind of object can be added without the reporting layer changing.
 *
 * [sessionIds] are the IDs current when the object's lifecycle ended. They are captured then rather than read when the leak
 * is reported, because confirmation happens later - and by then the current session part may not be the one the leak
 * belongs to.
 */
internal class LeakContext(
    val objectType: String,
    val sessionIds: SessionIdsSnapshot,
)
