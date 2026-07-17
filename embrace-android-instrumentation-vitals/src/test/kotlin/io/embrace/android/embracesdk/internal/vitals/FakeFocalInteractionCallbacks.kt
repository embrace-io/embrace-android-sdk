package io.embrace.android.embracesdk.internal.vitals

/** Test double that counts the signals delivered to a [FocalInteractionCallbacks]. */
internal class FakeFocalInteractionCallbacks : FocalInteractionCallbacks {
    var screenStartCount = 0
    var screenStopCount = 0
    var interactionStartCount = 0
    var interactionMoveCount = 0
    var interactionEndCount = 0
    var tapCount = 0
    var windowFocusedCount = 0
    var appBackgroundedCount = 0
    val navigationStarts = mutableListOf<String?>()
    val navigationEnds = mutableListOf<String?>()

    override fun onFrame(vsyncNanos: Long, frameDispatchNanos: Long, jankNanos: Long) {}
    override fun onScreenStart() { screenStartCount++ }
    override fun onScreenStop() { screenStopCount++ }
    override fun onInteractionStart() { interactionStartCount++ }
    override fun onInteractionMove() { interactionMoveCount++ }
    override fun onInteractionEnd() { interactionEndCount++ }
    override fun onTap(eventTime: Long) { tapCount++ }
    override fun onNavigationStart(screenName: String?) { navigationStarts += screenName }
    override fun onNavigationEnd(screenName: String?, eventTime: Long) { navigationEnds += screenName }
    override fun onWindowFocused() { windowFocusedCount++ }
    override fun onAppBackgrounded() { appBackgroundedCount++ }
}
