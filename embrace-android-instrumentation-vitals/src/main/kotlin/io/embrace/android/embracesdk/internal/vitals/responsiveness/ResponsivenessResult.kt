package io.embrace.android.embracesdk.internal.vitals.responsiveness

/**
 * Splits a slow response to a tap into where the time went: [tapToFrameMs] is from the raw touch event to
 * the next frame drawn in response; [frameToDrainMs] is from that frame to the main thread's queue next
 * draining, i.e. how much work was still backed up behind the frame.
 */
internal data class ResponsivenessResult(
    val startTimeMs: Long,
    val tapToFrameMs: Long,
    val frameToDrainMs: Long,
)
