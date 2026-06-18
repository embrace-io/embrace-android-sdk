package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi

/**
 * Forwards each frame's `(vsyncNanos, jankNanos)` to the [FocalInteractionCallbacks], on the Vitals handler thread.
 * Version-specific extraction is delegated to [frameMetricsStrategy].
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class VitalsFrameMetricsListener(
    private val focalCallbacks: FocalInteractionCallbacks,
    private val frameMetricsStrategy: FrameMetricsStrategy,
) : Window.OnFrameMetricsAvailableListener {

    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        try {
            val vsyncNanos = frameMetricsStrategy.vsyncNanos(frameMetrics)
            val jankNanos = frameMetricsStrategy.jankNanos(frameMetrics)
            focalCallbacks.onFrame(vsyncNanos, jankNanos)
        } catch (_: Throwable) {
        }
    }
}
