package io.embrace.android.embracesdk.internal.vitals

/** Test double that counts the signals delivered to a [FocalInteractionCallbacks]. */
internal class RecordingFocalInteractionCallbacks : FocalInteractionCallbacks {
    var screenStartCount = 0
    var screenStopCount = 0
    var interactionStartCount = 0
    var interactionMoveCount = 0
    var interactionEndCount = 0

    override fun onFrame(vsyncNanos: Long, jankNanos: Long) {}
    override fun onScreenStart() { screenStartCount++ }
    override fun onScreenStop() { screenStopCount++ }
    override fun onInteractionStart() { interactionStartCount++ }
    override fun onInteractionMove() { interactionMoveCount++ }
    override fun onInteractionEnd() { interactionEndCount++ }
}
