package io.embrace.android.embracesdk.internal.vitals

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.vitals.smoothness.FocalMomentTracker
import io.embrace.android.embracesdk.internal.vitals.smoothness.SmoothnessReporter
import io.embrace.android.embracesdk.internal.vitals.smoothness.SmoothnessResult

/**
 * Owns the OS plumbing for the smoothness vital: the frame-metrics listener and touch callback, both
 * feeding the single tracker. The tracker scopes a focal moment to a user interaction and reports
 * framerate quality over it.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class VitalsDataSource(
    private val args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy,
    instrumentationName = "vitals_data_source",
) {

    // Extracts per-frame jank; below API 31 the budget tracks the display's refresh interval.
    private val frameMetricsStrategy: FrameMetricsStrategy = FrameMetricsStrategy.create(displayRefreshIntervalNanos())

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                frameMetricsStrategy.onRefreshIntervalChanged(displayRefreshIntervalNanos())
            }
        }

        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

    private var handlerThread: HandlerThread? = null
    private var scheduler: VitalsScheduler? = null
    private var activityListener: VitalsActivityListener? = null

    override fun onDataCaptureEnabled() {
        val thread = HandlerThread(HANDLER_THREAD_NAME).apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)

        // Below API 31 the budget comes from [refreshRate]; track refresh-rate changes to keep it live.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            args.systemService<DisplayManager>(Context.DISPLAY_SERVICE)
                ?.registerDisplayListener(displayListener, handler)
        }

        val vitalsScheduler = HandlerVitalsScheduler(handler)
        scheduler = vitalsScheduler
        val tracker = FocalMomentTracker(
            scheduler = vitalsScheduler,
            reporter = SmoothnessReporter(emit = ::emitSmoothnessResult),
            clock = clock,
        )

        val listener = VitalsActivityListener(
            logger = logger,
            focalCallbacks = tracker,
            frameMetricsHandler = handler,
            frameMetricsStrategy = frameMetricsStrategy,
        )
        activityListener = listener
        args.application.registerActivityLifecycleCallbacks(listener)
    }

    override fun onDataCaptureDisabled() {
        activityListener?.let(args.application::unregisterActivityLifecycleCallbacks)
        activityListener = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            args.systemService<DisplayManager>(Context.DISPLAY_SERVICE)
                ?.unregisterDisplayListener(displayListener)
        }
        // Cancels any pending settle for an open focal moment.
        scheduler?.cancelSettle()
        scheduler = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private fun emitSmoothnessResult(result: SmoothnessResult) {
        captureTelemetry {
            recordCompletedSpan(
                name = "smoothness",
                startTimeMs = result.startTimeMs,
                endTimeMs = result.startTimeMs + result.durationMs,
                type = EmbType.Performance.Smoothness,
                private = true,
                attributes = SchemaType.Smoothness(
                    outcome = result.outcome.name.lowercase(),
                    frameCount = result.frameCount,
                    normalizedDroppedFrames = result.normalizedDroppedFrames,
                ).attributes(),
            )
        }
    }

    internal fun displayRefreshIntervalNanos(): Long {
        val refreshRate = runCatching {
            args.systemService<DisplayManager>(Context.DISPLAY_SERVICE)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.refreshRate
                ?: NORMALIZED_REFRESH_RATE
        }.getOrDefault(NORMALIZED_REFRESH_RATE)
        val rate = if (refreshRate > 0f) refreshRate else NORMALIZED_REFRESH_RATE
        return (NANOS_PER_SECOND / rate).toLong()
    }

    private companion object {
        const val HANDLER_THREAD_NAME = "emb-vitals"

        /**
         * Our "normalized" refresh rate is locked at 60fps, we adjust all frame rates to this so 1 frame on a 30fps screen counts as
         * 2 frames and 1 frame on a 120fps display rate is considered 0.5 frames.
         */
        const val NORMALIZED_REFRESH_RATE = 60f
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
