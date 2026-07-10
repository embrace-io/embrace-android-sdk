package io.embrace.android.embracesdk.internal.vitals

/**
 * Fans every [FocalInteractionCallbacks] signal out to each of [callbacks], in order. Lets independent
 * trackers (smoothness, screen-load, responsiveness, ...) each implement the single interface and be
 * wired into [VitalsActivityListener] together, without any one of them needing to know about the others.
 */
internal class CompositeFocalInteractionCallbacks(
    private val callbacks: List<FocalInteractionCallbacks>,
) : FocalInteractionCallbacks {

    override fun onFrame(vsyncNanos: Long, frameDispatchNanos: Long, jankNanos: Long) =
        callbacks.forEach { it.onFrame(vsyncNanos, frameDispatchNanos, jankNanos) }

    override fun onScreenStart() = callbacks.forEach { it.onScreenStart() }

    override fun onScreenStop() = callbacks.forEach { it.onScreenStop() }

    override fun onInteractionStart(eventTime: Long) = callbacks.forEach { it.onInteractionStart(eventTime) }

    override fun onInteractionMove() = callbacks.forEach { it.onInteractionMove() }

    override fun onInteractionEnd() = callbacks.forEach { it.onInteractionEnd() }

    override fun onTap(eventTime: Long) = callbacks.forEach { it.onTap(eventTime) }

    override fun onNavigationStart(screenName: String?) = callbacks.forEach { it.onNavigationStart(screenName) }

    override fun onNavigationEnd(screenName: String?) = callbacks.forEach { it.onNavigationEnd(screenName) }

    override fun onWindowFocused() = callbacks.forEach { it.onWindowFocused() }

    override fun onAppBackgrounded() = callbacks.forEach { it.onAppBackgrounded() }
}
